package io.github.otajisan.youtubehandleidconverter.conversion

import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeApiException
import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeChannel
import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeDataApiClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.system.measureTimeMillis

class ConversionServiceTest {
    private val client = mockk<YouTubeDataApiClient>()
    private val service = ConversionService(InputParser(), client)

    private val a = YouTubeChannel("UCaaaaaaaaaaaaaaaaaaaaaa", "@alpha", "A", "https://img/a")
    private val b = YouTubeChannel("UCbbbbbbbbbbbbbbbbbbbbbb", "@bravo", "B", null)

    @Test
    fun `混在入力を入力順に変換し、Quota 消費はハンドル件数 + (ID があれば 1) になる`() {
        every { client.byIds(listOf("UCaaaaaaaaaaaaaaaaaaaaaa", "UCbbbbbbbbbbbbbbbbbbbbbb")) } returns listOf(a, b)
        every { client.byHandle("alpha") } returns a
        every { client.byHandle("nobody") } throws YouTubeApiException.NotFound("@nobody")

        val results =
            service.convert(
                listOf(
                    "@alpha",
                    "UCaaaaaaaaaaaaaaaaaaaaaa",
                    "https://www.youtube.com/@nobody",
                    "not valid!",
                    "https://www.youtube.com/channel/UCbbbbbbbbbbbbbbbbbbbbbb",
                ),
            )

        assertThat(results.map { it.status }).containsExactly(
            ConversionResult.Status.OK,
            ConversionResult.Status.OK,
            ConversionResult.Status.NOT_FOUND,
            ConversionResult.Status.INVALID,
            ConversionResult.Status.OK,
        )
        assertThat(results[0]).isEqualTo(
            ConversionResult(
                "@alpha",
                ConversionResult.Status.OK,
                "@alpha",
                "UCaaaaaaaaaaaaaaaaaaaaaa",
                "A",
                "https://img/a",
            ),
        )
        assertThat(results[2].reason).isEqualTo("handle not found")
        assertThat(results[3].reason).isNotBlank()
        assertThat(results[4].channelId).isEqualTo("UCbbbbbbbbbbbbbbbbbbbbbb")

        // ハンドル 2 件(alpha, nobody)+ ID をまとめた 1 回 = 3 unit
        verify(exactly = 1) { client.byIds(any()) }
        verify(exactly = 2) { client.byHandle(any()) }
    }

    @Test
    fun `ID が無ければ byIds を呼ばない(消費はハンドル件数のみ)`() {
        every { client.byHandle("alpha") } returns a

        service.convert(listOf("@alpha"))

        verify(exactly = 0) { client.byIds(any()) }
        verify(exactly = 1) { client.byHandle("alpha") }
    }

    @Test
    fun `重複する入力は 1 回だけ API を呼び、大文字小文字の違うハンドルも同一視する`() {
        every { client.byIds(listOf("UCaaaaaaaaaaaaaaaaaaaaaa")) } returns listOf(a)
        every { client.byHandle(any()) } returns a

        val results =
            service.convert(
                listOf(
                    "@alpha",
                    "@ALPHA",
                    "UCaaaaaaaaaaaaaaaaaaaaaa",
                    "https://youtube.com/channel/UCaaaaaaaaaaaaaaaaaaaaaa",
                ),
            )

        assertThat(results).hasSize(4)
        assertThat(results.map { it.status }).containsOnly(ConversionResult.Status.OK)
        verify(exactly = 1) { client.byIds(any()) }
        verify(exactly = 1) { client.byHandle(any()) }
    }

    @Test
    fun `存在しない Channel ID は not_found`() {
        every { client.byIds(any()) } returns emptyList()

        val result = service.convert(listOf("UCmissingmissingmissingm")).single()

        assertThat(result.status).isEqualTo(ConversionResult.Status.NOT_FOUND)
    }

    @Test
    fun `不正な入力だけなら API を呼ばない`() {
        val results = service.convert(listOf("!!", ""))

        assertThat(results.map { it.status }).containsOnly(ConversionResult.Status.INVALID)
        verify(exactly = 0) { client.byIds(any()) }
        verify(exactly = 0) { client.byHandle(any()) }
    }

    @Test
    fun `多数のハンドルは並列に引く(100 件 × 100ms が数秒で終わる)`() {
        val inFlight = AtomicInteger()
        val maxInFlight = AtomicInteger()
        every { client.byHandle(any()) } answers {
            val now = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, now) }
            Thread.sleep(100)
            inFlight.decrementAndGet()
            YouTubeChannel("UC${firstArg<String>().padEnd(22, 'x').take(22)}", "@${firstArg<String>()}", "t", null)
        }
        val inputs = List(100) { "@handle$it" }

        val elapsed = measureTimeMillis { assertThat(service.convert(inputs)).hasSize(100) }

        // 逐次なら 10 秒以上。並列(同時 20)なら 0.5 秒程度
        assertThat(elapsed).isLessThan(3_000)
        assertThat(maxInFlight.get()).isBetween(2, ConversionService.HANDLE_CONCURRENCY)
        verify(exactly = 100) { client.byHandle(any()) }
    }

    @Test
    fun `並列に引いた一部のハンドルで上流障害が起きても全体を失敗させる`() {
        every { client.byHandle("alpha") } returns a
        every { client.byHandle("boom") } throws YouTubeApiException.Upstream("boom")

        assertThatThrownBy { service.convert(listOf("@alpha", "@boom")) }
            .isInstanceOf(YouTubeApiException.Upstream::class.java)
            .hasMessage("boom")
    }

    @Test
    fun `Quota 枯渇と上流障害は全体を失敗させる`() {
        every { client.byHandle("alpha") } throws YouTubeApiException.QuotaExceeded()
        assertThatThrownBy {
            service.convert(
                listOf("@alpha"),
            )
        }.isInstanceOf(YouTubeApiException.QuotaExceeded::class.java)

        every { client.byIds(any()) } throws YouTubeApiException.Upstream("boom")
        assertThatThrownBy { service.convert(listOf("UCaaaaaaaaaaaaaaaaaaaaaa")) }
            .isInstanceOf(YouTubeApiException.Upstream::class.java)
    }
}
