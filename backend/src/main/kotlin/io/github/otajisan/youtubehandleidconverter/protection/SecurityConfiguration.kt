package io.github.otajisan.youtubehandleidconverter.protection

import io.github.otajisan.youtubehandleidconverter.config.AppProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import tools.jackson.databind.ObjectMapper

/**
 * `APP_AUTH_ENABLED=true` のとき /api 配下に Basic 認証を要求する。
 * それ以外(OpenAPI 定義、preflight)は常に公開。API はステートレスなので CSRF / セッションは使わない。
 * Actuator は management ポート(ingress 非公開)で提供するためここでは扱わない。
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class SecurityConfiguration(
    private val properties: AppProperties,
    private val objectMapper: ObjectMapper,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors(Customizer.withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it.requestMatchers(HttpMethod.OPTIONS, "/api/**").permitAll()
                if (properties.auth.enabled) {
                    it.requestMatchers("/api/**").authenticated()
                }
                it.anyRequest().permitAll()
            }
        if (properties.auth.enabled) {
            http.httpBasic { it.authenticationEntryPoint(problemEntryPoint()) }
        }
        return http.build()
    }

    @Bean
    fun userDetailsService(): UserDetailsService {
        if (!properties.auth.enabled) return InMemoryUserDetailsManager()
        require(properties.auth.username.isNotBlank() && properties.auth.password.isNotBlank()) {
            "APP_AUTH_ENABLED=true requires APP_AUTH_USERNAME and APP_AUTH_PASSWORD"
        }
        val encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder()
        val user =
            User
                .withUsername(properties.auth.username)
                .password(encoder.encode(properties.auth.password))
                .roles("USER")
                .build()
        return InMemoryUserDetailsManager(user)
    }

    /** 401 を ProblemDetail で返す。WWW-Authenticate は付けるがブラウザのダイアログはフロント側で制御する */
    private fun problemEntryPoint() =
        AuthenticationEntryPoint { _, response, _ ->
            val problem =
                ProblemDetail
                    .forStatusAndDetail(HttpStatus.UNAUTHORIZED, "このツールは現在、認証が必要です。ユーザー名とパスワードを入力してください。")
                    .apply { title = "Authentication required" }
            response.writeProblem(
                objectMapper,
                problem,
                headers = mapOf("WWW-Authenticate" to "Basic realm=\"$REALM\""),
            )
        }

    companion object {
        private const val REALM = "youtube-handle-id-converter"
    }
}
