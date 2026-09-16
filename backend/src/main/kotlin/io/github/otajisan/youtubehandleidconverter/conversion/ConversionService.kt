package io.github.otajisan.youtubehandleidconverter.conversion

import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeApiException
import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeChannel
import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeDataApiClient
import org.springframework.stereotype.Service

/**
 * 複数の入力をまとめて変換する。Quota 消費を最小化するため、
 * Channel ID は 1 回の `byIds` にまとめ、ハンドルは 1 件ずつ `byHandle` で引く。
 * 消費 unit = ハンドル件数 + (Channel ID があれば 1)
 *
 * 個別の失敗(不正入力・未存在)は結果行の status で表し、
 * Quota 枯渇や上流障害は [YouTubeApiException] をそのまま投げて全体を失敗させる。
 */
@Service
class ConversionService(
    private val parser: InputParser,
    private val client: YouTubeDataApiClient,
) {
    fun convert(inputs: List<String>): List<ConversionResult> {
        val parsed = inputs.map { it to parser.parse(it) }

        val ids = parsed.mapNotNull { (it.second as? ParsedInput.ChannelId)?.id }.distinct()
        val byId: Map<String, YouTubeChannel> =
            ids
                .chunked(YouTubeDataApiClient.MAX_IDS_PER_REQUEST)
                .flatMap { client.byIds(it) }
                .associateBy { it.id }

        val byHandle = mutableMapOf<String, YouTubeChannel?>()
        val handles = parsed.mapNotNull { (it.second as? ParsedInput.Handle)?.handle }.distinctBy { it.lowercase() }
        handles.forEach { handle ->
            byHandle[handle.lowercase()] =
                try {
                    client.byHandle(handle)
                } catch (e: YouTubeApiException.NotFound) {
                    null
                }
        }

        return parsed.map { (raw, input) ->
            when (input) {
                is ParsedInput.Invalid -> {
                    ConversionResult(raw, ConversionResult.Status.INVALID, reason = input.reason)
                }

                is ParsedInput.ChannelId -> {
                    byId[input.id]?.toResult(raw)
                        ?: ConversionResult(raw, ConversionResult.Status.NOT_FOUND, reason = "channel not found")
                }

                is ParsedInput.Handle -> {
                    byHandle[input.handle.lowercase()]?.toResult(raw)
                        ?: ConversionResult(raw, ConversionResult.Status.NOT_FOUND, reason = "handle not found")
                }
            }
        }
    }

    private fun YouTubeChannel.toResult(raw: String) =
        ConversionResult(
            input = raw,
            status = ConversionResult.Status.OK,
            handle = handle,
            channelId = id,
            title = title,
            thumbnailUrl = thumbnailUrl,
        )
}
