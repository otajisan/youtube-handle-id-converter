package io.github.otajisan.youtubehandleidconverter.protection

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.assertj.MockMvcTester

@SpringBootTest(properties = ["youtube.api-key=test-api-key", "app.maintenance-mode=true"])
@AutoConfigureMockMvc
class MaintenanceModeTest {
    @Autowired
    private lateinit var mvc: MockMvcTester

    @Test
    fun `メンテナンスモードでは api 配下が 503 ProblemDetail を返す`() {
        val result = mvc.convert()

        result
            .assertThat()
            .hasStatus(HttpStatus.SERVICE_UNAVAILABLE)
            .hasHeader("Retry-After", "3600")
            .bodyJson()
            .extractingPath("$.title")
            .isEqualTo("Maintenance")
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
