package io.github.otajisan.youtubehandleidconverter.youtube

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

/** テストスライス(@RestClientTest 等)から @Import できるように、Properties の有効化を明示する */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(YouTubeProperties::class)
class YouTubeConfiguration
