package io.github.otajisan.youtubehandleidconverter.protection

import io.github.otajisan.youtubehandleidconverter.config.AppProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterProperties
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

/**
 * `APP_MAINTENANCE_MODE=true` のとき /api 配下を 503 で止める。
 * Spring Security の直後に置き、CORS ヘッダの付与と preflight 応答を先に済ませてから止める
 * (ブラウザが 503 の本文と Retry-After を読めるようにするため)。Actuator は management ポートなので対象外。
 */
@Component
@Order(SecurityFilterProperties.DEFAULT_FILTER_ORDER + 1)
class MaintenanceModeFilter(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest) =
        !properties.maintenanceMode || !isApiRequest(request.requestURI)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val problem =
            ProblemDetail
                .forStatusAndDetail(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "メンテナンス中のため変換を停止しています。しばらくしてから再度お試しください。",
                ).apply { title = "Maintenance" }
        response.writeProblem(objectMapper, problem, headers = mapOf("Retry-After" to RETRY_AFTER_SECONDS))
    }

    companion object {
        private const val RETRY_AFTER_SECONDS = "3600"
    }
}
