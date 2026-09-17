package io.github.otajisan.youtubehandleidconverter.api

import io.github.otajisan.youtubehandleidconverter.conversion.ConversionResult
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotEmpty

@Schema(description = "変換リクエスト")
data class ConvertRequest(
    @field:NotEmpty
    @field:Schema(
        description = "ハンドル(@handle)/ Channel ID(UC...)/ YouTube URL を混在可。件数の上限は APP_MAX_INPUTS(既定 100)",
        example = "[\"@youtube\", \"UC-9-kyTW8ZkZNDHQJ6FgpwQ\", \"https://www.youtube.com/@google\"]",
    )
    val inputs: List<String>,
)

@Schema(description = "変換レスポンス。入力順に 1 件ずつ結果を返す")
data class ConvertResponse(
    val results: List<ConvertResultDto>,
)

@Schema(description = "入力 1 件の変換結果")
data class ConvertResultDto(
    val input: String,
    @field:Schema(description = "ok / not_found / invalid", example = "ok")
    val status: String,
    @field:Schema(example = "@youtube")
    val handle: String?,
    @field:Schema(example = "UC-9-kyTW8ZkZNDHQJ6FgpwQ")
    val channelId: String?,
    val title: String?,
    val thumbnailUrl: String?,
    @field:Schema(description = "not_found / invalid の理由")
    val reason: String?,
) {
    companion object {
        fun from(r: ConversionResult) =
            ConvertResultDto(
                input = r.input,
                status = r.status.name.lowercase(),
                handle = r.handle,
                channelId = r.channelId,
                title = r.title,
                thumbnailUrl = r.thumbnailUrl,
                reason = r.reason,
            )
    }
}
