package io.github.otajisan.youtubehandleidconverter.protection

import com.github.benmanes.caffeine.cache.Caffeine
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
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
import java.time.Duration

/**
 * IP 単位のインメモリ・レートリミット(トークンバケット)。
 * Cloud Run のインスタンスごとに独立して数えるため厳密ではないが、単一 IP からの連打を抑える目的には足りる。
 * クライアント IP は Cloud Run が付与する X-Forwarded-For の先頭を使う。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RateLimitFilter(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
) : OncePerRequestFilter() {
    private val buckets =
        Caffeine
            .newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(10_000)
            .build<String, Bucket>()

    override fun shouldNotFilter(request: HttpServletRequest) =
        properties.rateLimitPerMinute <= 0 || !isApiRequest(request.requestURI) || request.method == "OPTIONS"

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val bucket = buckets.get(clientIp(request)) { newBucket() }
        val probe = bucket.tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) {
            filterChain.doFilter(request, response)
            return
        }
        val retryAfter = Duration.ofNanos(probe.nanosToWaitForRefill).seconds.coerceAtLeast(1)
        val problem =
            ProblemDetail
                .forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "リクエストが多すぎます。しばらく待ってから再度お試しください。")
                .apply {
                    title = "Rate limited"
                    setProperty("retryAfterSeconds", retryAfter)
                }
        response.writeProblem(objectMapper, problem, headers = mapOf("Retry-After" to retryAfter.toString()))
    }

    private fun newBucket(): Bucket =
        Bucket
            .builder()
            .addLimit(
                Bandwidth
                    .builder()
                    .capacity(properties.rateLimitPerMinute.toLong())
                    .refillGreedy(properties.rateLimitPerMinute.toLong(), Duration.ofMinutes(1))
                    .build(),
            ).build()

    private fun clientIp(request: HttpServletRequest): String =
        request
            .getHeader("X-Forwarded-For")
            ?.split(',')
            ?.firstOrNull()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: request.remoteAddr
}
