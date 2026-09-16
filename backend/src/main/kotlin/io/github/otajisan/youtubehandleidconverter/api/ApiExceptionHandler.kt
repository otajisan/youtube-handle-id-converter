package io.github.otajisan.youtubehandleidconverter.api

import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeApiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * エラーを RFC 9457 ProblemDetail で返す。バリデーション等の標準的なものは
 * [ResponseEntityExceptionHandler](Boot 4 は既定で ProblemDetail を返す)に任せる。
 */
@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(TooManyInputsException::class)
    fun tooManyInputs(e: TooManyInputsException): ProblemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.message ?: "too many inputs").apply {
            title = "Too many inputs"
            setProperty("max", e.max)
        }

    @ExceptionHandler(YouTubeApiException.QuotaExceeded::class)
    fun quotaExceeded(e: YouTubeApiException.QuotaExceeded): ProblemDetail {
        log.warn("YouTube Data API quota exceeded")
        val reset = nextQuotaReset()
        return ProblemDetail
            .forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                "YouTube Data API の 1 日の割り当てを使い切りました。太平洋時間 0 時($reset)にリセットされます。",
            ).apply {
                title = "Quota exceeded"
                setProperty("resetAt", reset.toOffsetDateTime().toString())
                setProperty("retryAfterSeconds", Duration.between(ZonedDateTime.now(QUOTA_ZONE), reset).seconds)
            }
    }

    @ExceptionHandler(YouTubeApiException.Upstream::class)
    fun upstream(e: YouTubeApiException.Upstream): ProblemDetail {
        log.error("YouTube Data API error: {}", e.message, e)
        return ProblemDetail
            .forStatusAndDetail(HttpStatus.BAD_GATEWAY, "YouTube Data API との通信に失敗しました。しばらくしてから再試行してください。")
            .apply { title = "Upstream error" }
    }

    /** Quota は太平洋時間の 0 時にリセットされる */
    private fun nextQuotaReset(): ZonedDateTime = LocalDate.now(QUOTA_ZONE).plusDays(1).atStartOfDay(QUOTA_ZONE)

    companion object {
        private val QUOTA_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")
    }
}
