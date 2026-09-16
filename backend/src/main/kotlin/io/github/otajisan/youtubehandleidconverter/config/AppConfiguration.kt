package io.github.otajisan.youtubehandleidconverter.config

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AppProperties::class)
class AppConfiguration
