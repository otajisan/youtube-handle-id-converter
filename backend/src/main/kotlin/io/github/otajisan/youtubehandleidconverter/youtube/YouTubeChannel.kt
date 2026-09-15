package io.github.otajisan.youtubehandleidconverter.youtube

/** `channels.list` から取り出した、このツールが必要とする最小限のチャンネル情報 */
data class YouTubeChannel(
    /** Channel ID(`UC` 始まり 24 文字) */
    val id: String,
    /** `@` 付きハンドル。ハンドル未設定のチャンネルは null */
    val handle: String?,
    val title: String,
    val thumbnailUrl: String?,
)
