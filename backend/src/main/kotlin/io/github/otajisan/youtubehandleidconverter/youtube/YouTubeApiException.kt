package io.github.otajisan.youtubehandleidconverter.youtube

/**
 * YouTube Data API 呼び出しの失敗を種別ごとに表す。
 * メッセージに URL や API Key を含めない(ログに残るため)。
 */
sealed class YouTubeApiException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    /** 指定したハンドルのチャンネルが存在しない(`items` が空) */
    class NotFound(
        val handle: String,
    ) : YouTubeApiException("channel not found for handle: $handle")

    /** 日次 Quota を使い切った(403 + reason=quotaExceeded) */
    class QuotaExceeded : YouTubeApiException("YouTube Data API quota exceeded")

    /** 上記以外の API エラー・通信エラー */
    class Upstream(
        message: String,
        cause: Throwable? = null,
    ) : YouTubeApiException(message, cause)
}
