package io.github.otajisan.youtubehandleidconverter.api

import com.ninjasquad.springmockk.MockkBean
import io.github.otajisan.youtubehandleidconverter.config.AppConfiguration
import io.github.otajisan.youtubehandleidconverter.config.CorsConfiguration
import io.github.otajisan.youtubehandleidconverter.conversion.ConversionResult
import io.github.otajisan.youtubehandleidconverter.conversion.ConversionService
import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeApiException
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester

@WebMvcTest(
    ConvertController::class,
    properties = ["app.max-inputs=3", "app.cors-allowed-origins=https://otajisan.github.io"],
)
@Import(AppConfiguration::class, CorsConfiguration::class)
class ConvertControllerTest {
    @Autowired
    private lateinit var mvc: MockMvcTester

    @MockkBean
    private lateinit var service: ConversionService

    @Test
    fun `正常系は 200 で入力順の結果を返す(status は小文字)`() {
        every { service.convert(listOf("@a", "bad")) } returns
            listOf(
                ConversionResult(
                    "@a",
                    ConversionResult.Status.OK,
                    "@a",
                    "UCaaaaaaaaaaaaaaaaaaaaaa",
                    "A",
                    "https://img/a",
                ),
                ConversionResult("bad", ConversionResult.Status.INVALID, reason = "not a handle"),
            )

        val result = post("""{"inputs":["@a","bad"]}""")

        result
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .isLenientlyEqualTo(
                """
                {"results":[
                  {"input":"@a","status":"ok","handle":"@a","channelId":"UCaaaaaaaaaaaaaaaaaaaaaa","title":"A","thumbnailUrl":"https://img/a","reason":null},
                  {"input":"bad","status":"invalid","handle":null,"channelId":null,"title":null,"thumbnailUrl":null,"reason":"not a handle"}
                ]}
                """.trimIndent(),
            )
    }

    @Test
    fun `上限を超える件数は 400 ProblemDetail`() {
        val result = post("""{"inputs":["@a","@b","@c","@d"]}""")

        result
            .assertThat()
            .hasStatus(HttpStatus.BAD_REQUEST)
            .bodyJson()
            .extractingPath("$.max")
            .isEqualTo(3)
        result
            .assertThat()
            .bodyJson()
            .extractingPath("$.title")
            .isEqualTo("Too many inputs")
    }

    @Test
    fun `inputs が空または欠落なら 400`() {
        post("""{"inputs":[]}""").assertThat().hasStatus(HttpStatus.BAD_REQUEST)
        post("""{}""").assertThat().hasStatus(HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Quota 枯渇は 429 ProblemDetail(リセット時刻付き)`() {
        every { service.convert(any()) } throws YouTubeApiException.QuotaExceeded()

        val result = post("""{"inputs":["@a"]}""")

        result.assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS)
        result
            .assertThat()
            .bodyJson()
            .extractingPath("$.title")
            .isEqualTo("Quota exceeded")
        result
            .assertThat()
            .bodyJson()
            .extractingPath("$.resetAt")
            .asString()
            .matches(""".*T00:00[-+]\d\d:\d\d""")
    }

    @Test
    fun `上流障害は 502 ProblemDetail`() {
        every { service.convert(any()) } throws YouTubeApiException.Upstream("boom")

        post("""{"inputs":["@a"]}""").assertThat().hasStatus(HttpStatus.BAD_GATEWAY)
    }

    @Test
    fun `許可オリジンからの preflight は CORS ヘッダを返し、他オリジンは拒否する`() {
        mvc
            .options()
            .uri("/api/v1/convert")
            .header("Origin", "https://otajisan.github.io")
            .header("Access-Control-Request-Method", "POST")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .hasHeader("Access-Control-Allow-Origin", "https://otajisan.github.io")

        mvc
            .options()
            .uri("/api/v1/convert")
            .header("Origin", "https://evil.example")
            .header("Access-Control-Request-Method", "POST")
            .exchange()
            .assertThat()
            .hasStatus(HttpStatus.FORBIDDEN)
    }

    private fun post(body: String) =
        mvc
            .post()
            .uri("/api/v1/convert")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .exchange()
}
