package io.github.otajisan.youtubehandleidconverter.protection

import org.springframework.http.MediaType
import org.springframework.test.web.servlet.assertj.MockMvcTester
import org.springframework.test.web.servlet.assertj.MvcTestResult

internal fun MockMvcTester.convert(
    body: String = """{"inputs":["not-valid!"]}""",
    configure: (org.springframework.test.web.servlet.assertj.MockMvcTester.MockMvcRequestBuilder) -> Unit = {},
): MvcTestResult =
    post()
        .uri("/api/v1/convert")
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .also(configure)
        .exchange()

internal fun MockMvcTester.preflight(origin: String = "https://otajisan.github.io"): MvcTestResult =
    options()
        .uri("/api/v1/convert")
        .header("Origin", origin)
        .header("Access-Control-Request-Method", "POST")
        .exchange()
