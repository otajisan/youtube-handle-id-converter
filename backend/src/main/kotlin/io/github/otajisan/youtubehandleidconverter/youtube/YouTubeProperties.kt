package io.github.otajisan.youtubehandleidconverter.youtube

import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * YouTube Data API v3 の接続設定。API Key は環境変数 `YOUTUBE_API_KEY` から注入する(Cloud Run では Secret Manager 経由)。
 */
@Validated
@ConfigurationProperties(prefix = "youtube")
data class YouTubeProperties(
    /** API Key。URL には含めず `x-goog-api-key` ヘッダで送る */
    @field:NotBlank val apiKey: String,
    val baseUrl: String = "https://www.googleapis.com/youtube/v3",
) {
    /** 誤ってログ等に出力しても API Key が漏れないようマスクする */
    override fun toString(): String = "YouTubeProperties(apiKey=****, baseUrl=$baseUrl)"
}
