package io.github.otajisan.youtubehandleidconverter.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(
    info =
        Info(
            title = "YouTube Handle ⇄ Channel ID Converter API",
            version = "v1",
            description = "YouTube のハンドル(@handle)と Channel ID(UC...)を相互変換する。/v3/api-docs で定義を公開",
        ),
)
class OpenApiConfiguration
