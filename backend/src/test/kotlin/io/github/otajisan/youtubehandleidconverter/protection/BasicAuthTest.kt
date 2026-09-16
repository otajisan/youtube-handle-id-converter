package io.github.otajisan.youtubehandleidconverter.protection

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.assertj.MockMvcTester
import java.util.Base64

@SpringBootTest(
    properties = [
        "youtube.api-key=test-api-key",
        "app.auth.enabled=true",
        "app.auth.username=operator",
        "app.auth.password=s3cret",
        "app.cors-allowed-origins=https://otajisan.github.io",
    ],
)
@AutoConfigureMockMvc
class BasicAuthTest {
    @Autowired
    private lateinit var mvc: MockMvcTester

    @Test
    fun `資格情報なしは 401 ProblemDetail と WWW-Authenticate`() {
        val result = mvc.convert()

        result
            .assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED)
            .hasHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"youtube-handle-id-converter\"")
            .bodyJson()
            .extractingPath("$.title")
            .isEqualTo("Authentication required")
    }

    @Test
    fun `誤った資格情報は 401`() {
        mvc
            .convert {
                it.header(HttpHeaders.AUTHORIZATION, basic("operator", "wrong"))
            }.assertThat()
            .hasStatus(HttpStatus.UNAUTHORIZED)
    }

    @Test
    fun `正しい資格情報なら通る(不正入力なので 200 invalid)`() {
        mvc
            .convert { it.header(HttpHeaders.AUTHORIZATION, basic("operator", "s3cret")) }
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.results[0].status")
            .isEqualTo("invalid")
    }

    @Test
    fun `CORS preflight は資格情報なしで通る`() {
        mvc.preflight().assertThat().hasStatusOk().hasHeader(
            "Access-Control-Allow-Origin",
            "https://otajisan.github.io",
        )
    }

    @Test
    fun `OpenAPI 定義は認証なしで参照できる`() {
        mvc
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .assertThat()
            .hasStatusOk()
    }

    private fun basic(
        user: String,
        password: String,
    ) = "Basic " + Base64.getEncoder().encodeToString("$user:$password".toByteArray())
}
