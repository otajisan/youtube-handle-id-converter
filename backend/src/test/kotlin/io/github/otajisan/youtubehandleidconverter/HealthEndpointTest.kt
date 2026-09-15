package io.github.otajisan.youtubehandleidconverter

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Actuator がアプリケーションとは別の management ポートでのみ提供されることを実サーバーで検証する。
 */
@SpringBootTest(
    webEnvironment = WebEnvironment.RANDOM_PORT,
    properties = ["management.server.port=0", "youtube.api-key=test-api-key"],
)
class HealthEndpointTest {
    @Value("\${local.server.port}")
    private var serverPort: Int = 0

    @Value("\${local.management.port}")
    private var managementPort: Int = 0

    private val client = HttpClient.newHttpClient()

    @Test
    fun `management ポートのヘルスチェックが UP を返す`() {
        val response = get(managementPort, "/actuator/health")

        assertThat(response.statusCode()).isEqualTo(200)
        assertThat(response.body()).contains("\"status\":\"UP\"")
    }

    @Test
    fun `liveness と readiness の probe が有効`() {
        assertThat(get(managementPort, "/actuator/health/liveness").statusCode()).isEqualTo(200)
        assertThat(get(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200)
    }

    @Test
    fun `アプリケーションポートでは Actuator に到達できない`() {
        assertThat(managementPort).isNotEqualTo(serverPort)
        assertThat(get(serverPort, "/actuator/health").statusCode()).isEqualTo(404)
    }

    private fun get(
        port: Int,
        path: String,
    ): HttpResponse<String> {
        val request = HttpRequest.newBuilder(URI.create("http://localhost:$port$path")).GET().build()
        return client.send(request, HttpResponse.BodyHandlers.ofString())
    }
}
