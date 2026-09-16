package io.github.otajisan.youtubehandleidconverter.conversion

/** 利用者の入力 1 件を解釈した結果 */
sealed interface ParsedInput {
    /** ハンドル(`@` 無しの正規化済み文字列) */
    data class Handle(
        val handle: String,
    ) : ParsedInput

    /** Channel ID(`UC` 始まり 24 文字) */
    data class ChannelId(
        val id: String,
    ) : ParsedInput

    /** 解釈できない入力 */
    data class Invalid(
        val reason: String,
    ) : ParsedInput
}
