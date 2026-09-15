package io.github.otajisan.youtubehandleidconverter.youtube

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.ObjectMapper

/**
 * YouTube Data API v3 `channels.list` の薄いクライアント。
 *
 * - ハンドル → チャンネル: [byHandle](1 件 / 1 unit)
 * - Channel ID → チャンネル: [byIds](最大 50 件 / 1 unit)
 *
 * API Key は `x-goog-api-key` ヘッダで送り、URL・例外メッセージ・ログに残さない。
 * タイムアウトは自動構成の [RestClient.Builder] に `spring.http.client.*` で設定する(テストではモックに差し替わる)。
 */
@Component
class YouTubeDataApiClient(
    builder: RestClient.Builder,
    properties: YouTubeProperties,
    private val objectMapper: ObjectMapper,
) {
    private val restClient: RestClient =
        builder
            .baseUrl(properties.baseUrl)
            .defaultHeader(API_KEY_HEADER, properties.apiKey)
            .build()

    /**
     * ハンドルからチャンネルを引く。先頭の `@` は有無どちらでもよい。
     * @throws YouTubeApiException.NotFound 該当チャンネルが無い
     */
    fun byHandle(handle: String): YouTubeChannel {
        val normalized = handle.removePrefix("@")
        val response =
            call {
                restClient
                    .get()
                    // 値はテンプレート変数として渡し、{ } 等を厳密にエンコードさせる(テンプレートとして解釈させない)
                    .uri("/channels?part=id,snippet&forHandle={handle}", normalized)
                    .retrieve()
                    .body(ChannelListResponse::class.java)
            }
        return response?.items?.firstOrNull()?.toChannel() ?: throw YouTubeApiException.NotFound("@$normalized")
    }

    /**
     * Channel ID の一覧からチャンネルを引く。存在しない ID は結果に含まれない(例外にしない)。
     * @param ids 最大 [MAX_IDS_PER_REQUEST] 件
     */
    fun byIds(ids: List<String>): List<YouTubeChannel> {
        require(ids.size <= MAX_IDS_PER_REQUEST) { "at most $MAX_IDS_PER_REQUEST ids per request, got ${ids.size}" }
        if (ids.isEmpty()) return emptyList()
        val response =
            call {
                restClient
                    .get()
                    .uri("/channels?part=id,snippet&id={ids}", ids.joinToString(","))
                    .retrieve()
                    .body(ChannelListResponse::class.java)
            }
        return response?.items?.map { it.toChannel() } ?: emptyList()
    }

    private fun <T> call(block: () -> T): T =
        try {
            block()
        } catch (e: RestClientResponseException) {
            throw mapHttpError(e)
        } catch (e: ResourceAccessException) {
            // ResourceAccessException のメッセージにはリクエスト URL が含まれるため、根本原因だけを引き継ぐ
            val root = e.cause ?: e
            throw YouTubeApiException.Upstream("I/O error calling YouTube Data API: ${root.javaClass.simpleName}", root)
        }

    private fun mapHttpError(e: RestClientResponseException): YouTubeApiException {
        val reasons = parseErrorReasons(e.responseBodyAsString)
        return if (e.statusCode == HttpStatus.FORBIDDEN && QUOTA_EXCEEDED_REASON in reasons) {
            YouTubeApiException.QuotaExceeded()
        } else {
            YouTubeApiException.Upstream("YouTube Data API returned ${e.statusCode.value()} (reasons=$reasons)")
        }
    }

    private fun parseErrorReasons(body: String): List<String> =
        runCatching { objectMapper.readValue(body, GoogleApiErrorResponse::class.java) }
            .getOrNull()
            ?.error
            ?.errors
            ?.mapNotNull { it.reason }
            .orEmpty()

    private fun ChannelItem.toChannel() =
        YouTubeChannel(
            id = id,
            handle = snippet.customUrl?.takeIf { it.isNotBlank() },
            title = snippet.title,
            thumbnailUrl = snippet.thumbnails?.preferred?.url,
        )

    companion object {
        const val MAX_IDS_PER_REQUEST = 50
        const val API_KEY_HEADER = "x-goog-api-key"
        private const val QUOTA_EXCEEDED_REASON = "quotaExceeded"
    }
}
