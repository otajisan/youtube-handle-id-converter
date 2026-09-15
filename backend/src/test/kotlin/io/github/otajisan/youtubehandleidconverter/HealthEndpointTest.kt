package io.github.otajisan.youtubehandleidconverter

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointTest {
    @Autowired
    private lateinit var mvc: MockMvcTester

    @Test
    fun `ヘルスチェックが UP を返す`() {
        val result = mvc.get().uri("/actuator/health").exchange()

        result
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.status")
            .isEqualTo("UP")
    }
}
