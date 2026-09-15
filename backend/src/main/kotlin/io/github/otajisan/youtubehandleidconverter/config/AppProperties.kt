package io.github.otajisan.youtubehandleidconverter.config

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
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
)
