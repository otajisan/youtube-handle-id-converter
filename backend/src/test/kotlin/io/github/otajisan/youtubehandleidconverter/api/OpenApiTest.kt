package io.github.otajisan.youtubehandleidconverter.api

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.assertj.MockMvcTester

@SpringBootTest(properties = ["youtube.api-key=test-api-key"])
@AutoConfigureMockMvc
class OpenApiTest {
    @Autowired
    private lateinit var mvc: MockMvcTester

    @Test
    fun `OpenAPI 定義が参照できる`() {
        mvc
            .get()
            .uri("/v3/api-docs")
            .exchange()
            .assertThat()
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$.paths['/api/v1/convert'].post.summary")
            .asString()
            .contains("一括変換")
    }
}
