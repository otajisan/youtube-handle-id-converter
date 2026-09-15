package io.github.otajisan.youtubehandleidconverter.conversion

/** 入力 1 件に対する変換結果。全体のエラー(Quota 枯渇など)は例外で表す */
data class ConversionResult(
    val input: String,
    val status: Status,
    val handle: String? = null,
    val channelId: String? = null,
    val title: String? = null,
    val thumbnailUrl: String? = null,
    /** `invalid` / `not_found` の理由(利用者向け) */
    val reason: String? = null,
) {
    enum class Status {
        OK,
        NOT_FOUND,
        INVALID,
    }
}
