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
        "app.rate-limit-per-minute=3",
        "app.cors-allowed-origins=https://otajisan.github.io",
    ],
)
@AutoConfigureMockMvc
class RateLimitTest {
    @Autowired
    private lateinit var mvc: MockMvcTester

    @Test
    fun `同一 IP からの上限超過は 429(CORS ヘッダ付き)、別 IP は影響を受けず、preflight は数えない`() {
        // 先頭(クライアントが書ける値)を変えても、末尾(Cloud Run が付与する実 IP)が同じなら同一バケット
        repeat(3) { i ->
            mvc
                .convert {
                    it
                        .header(
                            "X-Forwarded-For",
                            "10.0.0.$i, 203.0.113.10",
                        ).header("Origin", "https://otajisan.github.io")
                }.assertThat()
                .hasStatusOk()
        }

        val limited =
            mvc.convert {
                it
                    .header(
                        "X-Forwarded-For",
                        "10.0.0.99, 203.0.113.10",
                    ).header("Origin", "https://otajisan.github.io")
            }
        limited
            .assertThat()
            .hasStatus(HttpStatus.TOO_MANY_REQUESTS)
            .hasHeader("Access-Control-Allow-Origin", "https://otajisan.github.io")
            .bodyJson()
            .extractingPath("$.title")
            .isEqualTo("Rate limited")
        limited.assertThat().headers().containsHeader("Retry-After")

        mvc.convert { it.header("X-Forwarded-For", "203.0.113.11") }.assertThat().hasStatusOk()
        mvc.preflight().assertThat().hasStatusOk()
    }
}
