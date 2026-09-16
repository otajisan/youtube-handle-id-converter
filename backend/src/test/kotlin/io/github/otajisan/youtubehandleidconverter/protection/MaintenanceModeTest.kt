package io.github.otajisan.youtubehandleidconverter.protection

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.assertj.MockMvcTester

@SpringBootTest(
    properties = [
        "youtube.api-key=test-api-key",
        "app.maintenance-mode=true",
        "app.cors-allowed-origins=https://otajisan.github.io",
    ],
)
@AutoConfigureMockMvc
class MaintenanceModeTest {
    @Autowired
    private lateinit var mvc: MockMvcTester

    @Test
    fun `メンテナンスモードでは api 配下が 503 ProblemDetail を返す(CORS ヘッダ付き)`() {
        val result = mvc.convert { it.header("Origin", "https://otajisan.github.io") }

        result
            .assertThat()
            .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
            .hasHeader("Retry-After", "3600")
            .hasHeader("Access-Control-Allow-Origin", "https://otajisan.github.io")
            .bodyJson()
            .extractingPath("$.title")
            .isEqualTo("Maintenance")
    }

    @Test
    fun `メンテナンス中でも CORS preflight は通る(ブラウザが 503 本文を読めるように)`() {
        mvc.preflight().assertThat().hasStatusOk().hasHeader(
            "Access-Control-Allow-Origin",
            "https://otajisan.github.io",
        )
    }

    @Test
    fun `api 配下以外(OpenAPI 定義)は影響を受けない`() {
        mvc
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .assertThat()
            .hasStatusOk()
    }
}
