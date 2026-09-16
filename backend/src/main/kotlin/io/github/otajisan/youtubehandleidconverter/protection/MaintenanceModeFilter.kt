package io.github.otajisan.youtubehandleidconverter.protection

import io.github.otajisan.youtubehandleidconverter.config.AppProperties
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.ObjectMapper

/**
 * `APP_MAINTENANCE_MODE=true` のとき /api 配下を 503 で止める。
 * 認証より前(最優先)に動くので、誰に対しても即座に効く。Actuator は management ポートなので対象外。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
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
