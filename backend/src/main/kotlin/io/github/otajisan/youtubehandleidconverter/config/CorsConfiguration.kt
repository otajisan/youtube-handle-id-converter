package io.github.otajisan.youtubehandleidconverter.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

// api 配下を許可オリジン(GitHub Pages)からのみブラウザ経由で呼べるようにする
@Configuration(proxyBeanMethods = false)
class CorsConfiguration(
    private val properties: AppProperties,
) : WebMvcConfigurer {
    override fun addCorsMappings(registry: CorsRegistry) {
        if (properties.corsAllowedOrigins.isEmpty()) return
        registry
            .addMapping("/api/**")
            .allowedOrigins(*properties.corsAllowedOrigins.toTypedArray())
            .allowedMethods("GET", "POST", "OPTIONS")
            .allowedHeaders("Content-Type", "Authorization")
            .maxAge(3600)
    }
}
