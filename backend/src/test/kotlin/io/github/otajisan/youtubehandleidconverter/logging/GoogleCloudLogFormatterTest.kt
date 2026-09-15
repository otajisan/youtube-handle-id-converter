package io.github.otajisan.youtubehandleidconverter.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.LoggingEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper

class GoogleCloudLogFormatterTest {
    private val formatter = GoogleCloudLogFormatter()

    // 素の LoggerContext には MDC アダプタが無いため、初期化済みの実コンテキストから Logger を取る
    private val logger = LoggerFactory.getLogger("test.logger") as Logger
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `Cloud Logging が解釈するフィールドを持つ 1 行 JSON を出力する`() {
        val event = LoggingEvent("fqcn", logger, Level.INFO, "hello {}", null, arrayOf("world"))

        val line = formatter.format(event)

        assertThat(line).endsWith(System.lineSeparator())
        val json = mapper.readTree(line)
        assertThat(json["severity"].asString()).isEqualTo("INFO")
        assertThat(json["message"].asString()).isEqualTo("hello world")
        assertThat(json["logger"].asString()).isEqualTo("test.logger")
        assertThat(json["timestamp"].asString()).isNotBlank()
        assertThat(json["thread"].asString()).isNotBlank()
        assertThat(json.has("context")).isFalse()
    }

    @Test
    fun `例外があればスタックトレースを message に含める`() {
        val event = LoggingEvent("fqcn", logger, Level.ERROR, "boom", IllegalStateException("cause"), null)

        val json = mapper.readTree(formatter.format(event))

        assertThat(json["severity"].asString()).isEqualTo("ERROR")
        assertThat(json["message"].asString())
            .startsWith("boom")
            .contains("java.lang.IllegalStateException: cause")
            .contains("GoogleCloudLogFormatterTest")
    }

    @Test
    fun `MDC があれば context として出力する`() {
        val event = LoggingEvent("fqcn", logger, Level.WARN, "with mdc", null, null)
        event.mdcPropertyMap = mapOf("requestId" to "abc")

        val json = mapper.readTree(formatter.format(event))

        assertThat(json["context"]["requestId"].asString()).isEqualTo("abc")
    }

    @ParameterizedTest
    @CsvSource("ERROR,ERROR", "WARN,WARNING", "INFO,INFO", "DEBUG,DEBUG", "TRACE,DEBUG")
    fun `Logback レベルを Cloud Logging の severity に対応付ける`(
        level: String,
        expected: String,
    ) {
        assertThat(GoogleCloudLogFormatter.toSeverity(Level.toLevel(level))).isEqualTo(expected)
    }
}
