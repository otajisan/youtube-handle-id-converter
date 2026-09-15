package io.github.otajisan.youtubehandleidconverter.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import org.springframework.boot.json.JsonWriter
import org.springframework.boot.logging.structured.StructuredLogFormatter

/**
 * Cloud Logging が特別扱いするフィールド(severity / message / timestamp)に合わせた
 * 1 行 JSON フォーマッタ。Cloud Run の標準出力に流すと自動でパースされる。
 *
 * https://cloud.google.com/logging/docs/structured-logging
 */
class GoogleCloudLogFormatter : StructuredLogFormatter<ILoggingEvent> {
    private val writer: JsonWriter<ILoggingEvent> =
        JsonWriter
            .of<ILoggingEvent> { members ->
                members.add("timestamp") { event: ILoggingEvent -> event.instant.toString() }
                members.add("severity") { event: ILoggingEvent -> toSeverity(event.level) }
                members.add("message") { event: ILoggingEvent -> messageWithStackTrace(event) }
                members.add("logger") { event: ILoggingEvent -> event.loggerName }
                members.add("thread") { event: ILoggingEvent -> event.threadName }
                members.add("context") { event: ILoggingEvent -> event.mdcPropertyMap }.whenNotEmpty()
            }.withNewLineAtEnd()

    override fun format(event: ILoggingEvent): String = writer.write(event).toJsonString()

    private fun messageWithStackTrace(event: ILoggingEvent): String {
        val throwable = event.throwableProxy ?: return event.formattedMessage
        return event.formattedMessage + System.lineSeparator() + ThrowableProxyUtil.asString(throwable)
    }

    companion object {
        /** Logback のレベルを Cloud Logging の LogSeverity に対応付ける。 */
        fun toSeverity(level: Level): String =
            when (level.toInt()) {
                Level.ERROR_INT -> "ERROR"
                Level.WARN_INT -> "WARNING"
                Level.INFO_INT -> "INFO"
                else -> "DEBUG"
            }
    }
}
