package io.github.otajisan.youtubehandleidconverter.conversion

import org.springframework.stereotype.Component
import java.net.URI

/**
 * 入力文字列をハンドル / Channel ID / YouTube URL のいずれかとして解釈する。
 *
 * - Channel ID: `UC` + 22 文字(`[A-Za-z0-9_-]`)
 * - ハンドル: 3〜30 文字の `[A-Za-z0-9_.-]`、先頭の `@` は任意
 * - URL: `youtube.com/@handle`、`youtube.com/channel/UC...`(`www.` / `m.` / `http(s)` の有無は不問)
 */
@Component
class InputParser {
    fun parse(raw: String): ParsedInput {
        val value = raw.trim()
        if (value.isEmpty()) return ParsedInput.Invalid("empty")
        if (value.length > MAX_INPUT_LENGTH) return ParsedInput.Invalid("too long")

        if (looksLikeUrl(value)) return parseUrl(value)
        if (CHANNEL_ID.matches(value)) return ParsedInput.ChannelId(value)
        val handle = value.removePrefix("@")
        if (HANDLE.matches(handle)) return ParsedInput.Handle(handle)
        return ParsedInput.Invalid("not a handle, channel id, or youtube url")
    }

    private fun looksLikeUrl(value: String) = value.contains("youtube.com/", ignoreCase = true)

    private fun parseUrl(value: String): ParsedInput {
        val withScheme = if (value.contains("://")) value else "https://$value"
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return ParsedInput.Invalid("malformed url")
        val host = uri.host?.lowercase() ?: return ParsedInput.Invalid("malformed url")
        if (host !in YOUTUBE_HOSTS) return ParsedInput.Invalid("not a youtube.com url")

        val segments =
            uri.path
                .orEmpty()
                .split('/')
                .filter { it.isNotEmpty() }
        return when {
            segments.isEmpty() -> {
                ParsedInput.Invalid("url has no handle or channel id")
            }

            segments[0].startsWith("@") -> {
                val handle = segments[0].removePrefix("@")
                if (HANDLE.matches(handle)) ParsedInput.Handle(handle) else ParsedInput.Invalid("invalid handle in url")
            }

            segments[0] == "channel" && segments.size >= 2 && CHANNEL_ID.matches(segments[1]) -> {
                ParsedInput.ChannelId(segments[1])
            }

            else -> {
                ParsedInput.Invalid("url has no handle or channel id")
            }
        }
    }

    companion object {
        const val MAX_INPUT_LENGTH = 200
        private val CHANNEL_ID = Regex("^UC[A-Za-z0-9_-]{22}$")
        private val HANDLE = Regex("^[A-Za-z0-9_.-]{3,30}$")
        private val YOUTUBE_HOSTS = setOf("youtube.com", "www.youtube.com", "m.youtube.com")
    }
}
