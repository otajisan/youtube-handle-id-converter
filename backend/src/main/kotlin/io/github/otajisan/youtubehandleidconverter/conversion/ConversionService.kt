package io.github.otajisan.youtubehandleidconverter.conversion

import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeApiException
import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeChannel
import io.github.otajisan.youtubehandleidconverter.youtube.YouTubeDataApiClient
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore

/**
 * 複数の入力をまとめて変換する。Quota 消費を最小化するため、
 * Channel ID は 50 件ずつ `byIds` にまとめ、ハンドルは 1 件ずつ `byHandle` で引く。
 * 消費 unit = ハンドル件数 + ⌈Channel ID 件数 / 50⌉
 *
 * ハンドルは件数分の API 呼び出しになるため、仮想スレッドで並列に引く(同時実行は [HANDLE_CONCURRENCY] まで)。
 * 個別の失敗(不正入力・未存在)は結果行の status で表し、
 * Quota 枯渇や上流障害は [YouTubeApiException] をそのまま投げて全体を失敗させる。
 */
@Service
class ConversionService(
    private val parser: InputParser,
    private val client: YouTubeDataApiClient,
) {
    private val executor = Executors.newVirtualThreadPerTaskExecutor()
    private val handleConcurrency = Semaphore(HANDLE_CONCURRENCY)

    fun convert(inputs: List<String>): List<ConversionResult> {
        val parsed = inputs.map { it to parser.parse(it) }

        val ids = parsed.mapNotNull { (it.second as? ParsedInput.ChannelId)?.id }.distinct()
        val byId: Map<String, YouTubeChannel> =
            ids
                .chunked(YouTubeDataApiClient.MAX_IDS_PER_REQUEST)
                .flatMap { client.byIds(it) }
                .associateBy { it.id }

        val handles = parsed.mapNotNull { (it.second as? ParsedInput.Handle)?.handle }.distinctBy { it.lowercase() }
        val byHandle: Map<String, YouTubeChannel?> = lookupHandles(handles)

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

    /** ハンドルを並列に引く。未存在は null、それ以外の API エラーは呼び出し元に伝播させる */
    private fun lookupHandles(handles: List<String>): Map<String, YouTubeChannel?> {
        if (handles.isEmpty()) return emptyMap()
        val futures =
            handles.map { handle ->
                executor.submit<YouTubeChannel?> {
                    handleConcurrency.acquire()
                    try {
                        client.byHandle(handle)
                    } catch (e: YouTubeApiException.NotFound) {
                        null
                    } finally {
                        handleConcurrency.release()
                    }
                }
            }
        return handles.zip(futures).associate { (handle, future) ->
            handle.lowercase() to
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
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

    companion object {
        /** YouTube API へ同時に投げるハンドル検索の上限(上流への負荷と接続数を抑える) */
        const val HANDLE_CONCURRENCY = 20
    }
}
