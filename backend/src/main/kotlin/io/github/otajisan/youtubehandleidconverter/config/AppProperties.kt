package io.github.otajisan.youtubehandleidconverter.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.PositiveOrZero
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * アプリケーション固有の設定。環境変数 `APP_*` で上書きする(Cloud Run では Terraform が設定)。
 */
@Validated
@ConfigurationProperties(prefix = "app")
data class AppProperties(
    /** 1 リクエストで受け付ける入力件数の上限。Quota 利用状況を見て調整する */
    @field:Min(1) @field:Max(50) val maxInputs: Int = 10,
    /** CORS で許可するオリジン(GitHub Pages)。空なら CORS を許可しない */
    val corsAllowedOrigins: List<String> = emptyList(),
    /** true なら /api 配下が 503 を返す(Quota 枯渇時などの緊急停止) */
    val maintenanceMode: Boolean = false,
    /** Basic 認証。デフォルト無効で、bot 等による Quota 枯渇が続く場合に ON にする */
    val auth: Auth = Auth(),
    /** IP ごとの 1 分あたりリクエスト上限。0 で無効 */
    @field:PositiveOrZero val rateLimitPerMinute: Int = 30,
) {
    data class Auth(
        val enabled: Boolean = false,
        /** 資格情報は Secret Manager から環境変数 APP_AUTH_USERNAME / APP_AUTH_PASSWORD で注入 */
        val username: String = "",
        val password: String = "",
    ) {
        /** ログ等に資格情報が出ないようにする */
        override fun toString(): String = "Auth(enabled=$enabled, username=****, password=****)"
    }

    override fun toString(): String =
        "AppProperties(maxInputs=$maxInputs, corsAllowedOrigins=$corsAllowedOrigins, " +
            "maintenanceMode=$maintenanceMode, auth=$auth, rateLimitPerMinute=$rateLimitPerMinute)"
}
