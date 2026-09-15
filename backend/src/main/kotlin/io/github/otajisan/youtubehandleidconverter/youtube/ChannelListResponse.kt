package io.github.otajisan.youtubehandleidconverter.youtube

/** `channels.list` のレスポンス(必要なフィールドのみ) */
internal data class ChannelListResponse(
    val items: List<ChannelItem> = emptyList(),
)

internal data class ChannelItem(
    val id: String,
    val snippet: ChannelSnippet,
)

internal data class ChannelSnippet(
    val title: String,
    /** `@handle` 形式。ハンドル未設定のチャンネルでは存在しない */
    val customUrl: String? = null,
    val thumbnails: Thumbnails? = null,
)

internal data class Thumbnails(
    val default: Thumbnail? = null,
    val medium: Thumbnail? = null,
    val high: Thumbnail? = null,
) {
    /** 表示用に最も小さいものを優先して返す */
    val preferred: Thumbnail? get() = default ?: medium ?: high
}

internal data class Thumbnail(
    val url: String,
)

/** Google API 共通のエラーレスポンス */
internal data class GoogleApiErrorResponse(
    val error: GoogleApiError? = null,
)

internal data class GoogleApiError(
    val code: Int? = null,
    val message: String? = null,
    val errors: List<GoogleApiErrorDetail> = emptyList(),
)

internal data class GoogleApiErrorDetail(
    val reason: String? = null,
    val message: String? = null,
)
