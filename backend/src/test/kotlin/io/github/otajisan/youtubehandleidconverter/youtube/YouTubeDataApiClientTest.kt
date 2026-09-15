package io.github.otajisan.youtubehandleidconverter.youtube

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import java.net.SocketTimeoutException

@RestClientTest(YouTubeDataApiClient::class)
@Import(YouTubeConfiguration::class)
class YouTubeDataApiClientTest {
    @Autowired
    private lateinit var client: YouTubeDataApiClient

    @Autowired
    private lateinit var server: MockRestServiceServer

    @Test
    fun `ハンドルからチャンネルを引ける(API Key はヘッダで送り URL に含めない)`() {
        server
            .expect(requestTo(containsString("/channels?part=id,snippet&forHandle=example")))
            .andExpect(requestTo(not(containsString("key="))))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header(YouTubeDataApiClient.API_KEY_HEADER, "test-api-key"))
            .andRespond(
                withSuccess(
                    channelList(channel("UCxxxxxxxxxxxxxxxxxxxxxx", "Example", "@example")),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.byHandle("@example")

        assertThat(result).isEqualTo(
            YouTubeChannel(
                id = "UCxxxxxxxxxxxxxxxxxxxxxx",
                handle = "@example",
                title = "Example",
                thumbnailUrl = "https://yt3.ggpht.com/example=s88",
            ),
        )
    }

    @Test
    fun `先頭の @ が無くても引ける`() {
        server
            .expect(queryParam("forHandle", "example"))
            .andRespond(
                withSuccess(
                    channelList(channel("UCxxxxxxxxxxxxxxxxxxxxxx", "Example", "@example")),
                    MediaType.APPLICATION_JSON,
                ),
            )

        assertThat(client.byHandle("example").id).isEqualTo("UCxxxxxxxxxxxxxxxxxxxxxx")
    }

    @Test
    fun `存在しないハンドルは NotFound(items が空)`() {
        server
            .expect(
                queryParam("forHandle", "nobody"),
            ).andRespond(withSuccess("""{"items":[]}""", MediaType.APPLICATION_JSON))

        assertThatThrownBy { client.byHandle("@nobody") }
            .isInstanceOf(YouTubeApiException.NotFound::class.java)
            .hasMessageContaining("@nobody")
    }

    @Test
    fun `Channel ID の一覧をまとめて引ける(存在しない ID は結果に含まれない)`() {
        server
            .expect(queryParam("id", "UCaaaaaaaaaaaaaaaaaaaaaa,UCbbbbbbbbbbbbbbbbbbbbbb,UCmissingmissingmissingm"))
            .andRespond(
                withSuccess(
                    channelList(
                        channel("UCaaaaaaaaaaaaaaaaaaaaaa", "A", "@a"),
                        channel("UCbbbbbbbbbbbbbbbbbbbbbb", "B", "@b"),
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result =
            client.byIds(
                listOf("UCaaaaaaaaaaaaaaaaaaaaaa", "UCbbbbbbbbbbbbbbbbbbbbbb", "UCmissingmissingmissingm"),
            )

        assertThat(result.map { it.id }).containsExactly("UCaaaaaaaaaaaaaaaaaaaaaa", "UCbbbbbbbbbbbbbbbbbbbbbb")
    }

    @Test
    fun `customUrl が無いチャンネルは handle が null`() {
        server
            .expect(queryParam("id", "UCnohandlenohandlenohand"))
            .andRespond(
                withSuccess(
                    channelList(channel("UCnohandlenohandlenohand", "No Handle", null)),
                    MediaType.APPLICATION_JSON,
                ),
            )

        val result = client.byIds(listOf("UCnohandlenohandlenohand")).single()

        assertThat(result.handle).isNull()
        assertThat(result.title).isEqualTo("No Handle")
    }

    @Test
    fun `空の ID 一覧は API を呼ばない`() {
        assertThat(client.byIds(emptyList())).isEmpty()
        server.verify()
    }

    @Test
    fun `50 件を超える ID は受け付けない`() {
        assertThatThrownBy { client.byIds(List(51) { "UC$it" }) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `403 quotaExceeded は QuotaExceeded`() {
        server.expect(queryParam("forHandle", "x")).andRespond(
            withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body(
                """{"error":{"code":403,"message":"quota","errors":[{"reason":"quotaExceeded","message":"The request cannot be completed because you have exceeded your quota."}]}}""",
            ),
        )

        assertThatThrownBy { client.byHandle("x") }.isInstanceOf(YouTubeApiException.QuotaExceeded::class.java)
    }

    @Test
    fun `quotaExceeded 以外の 403 や 5xx は Upstream`() {
        server.expect(queryParam("forHandle", "x")).andRespond(
            withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body(
                """{"error":{"code":403,"errors":[{"reason":"accessNotConfigured"}]}}""",
            ),
        )

        assertThatThrownBy { client.byHandle("x") }
            .isInstanceOf(YouTubeApiException.Upstream::class.java)
            .hasMessageContaining("403")
            .hasMessageContaining("accessNotConfigured")
    }

    @Test
    fun `タイムアウトなどの通信エラーは Upstream(メッセージに URL を含めない)`() {
        server.expect(queryParam("forHandle", "x")).andRespond { throw SocketTimeoutException("Read timed out") }

        assertThatThrownBy { client.byHandle("x") }
            .isInstanceOf(YouTubeApiException.Upstream::class.java)
            .hasMessageContaining("SocketTimeoutException")
            .hasMessageNotContaining("/channels")
            .hasCauseInstanceOf(SocketTimeoutException::class.java)
    }

    private fun channelList(vararg items: String) =
        """{"kind":"youtube#channelListResponse","items":[${items.joinToString(",")}]}"""

    private fun channel(
        id: String,
        title: String,
        customUrl: String?,
    ): String {
        val custom = customUrl?.let { ""","customUrl":"$it"""" } ?: ""
        val thumbnails = """{"default":{"url":"https://yt3.ggpht.com/example=s88","width":88,"height":88}}"""
        return """{"kind":"youtube#channel","id":"$id","snippet":{"title":"$title"$custom,"thumbnails":$thumbnails}}"""
    }
}
