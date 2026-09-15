package io.github.otajisan.youtubehandleidconverter.api

import io.github.otajisan.youtubehandleidconverter.config.AppProperties
import io.github.otajisan.youtubehandleidconverter.conversion.ConversionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
@Tag(name = "convert", description = "YouTube ハンドルと Channel ID の相互変換")
class ConvertController(
    private val service: ConversionService,
    private val properties: AppProperties,
) {
    @PostMapping("/convert")
    @Operation(
        summary = "ハンドル / Channel ID / URL を一括変換する",
        description =
            "入力ごとの結果を入力順に返す(200)。個別の不正入力・未存在は行の status で表す。" +
                "Quota 枯渇は 429、YouTube API の障害は 502、件数超過は 400。",
    )
    @ApiResponse(responseCode = "200", description = "変換結果")
    @ApiResponse(responseCode = "400", description = "リクエスト不正(件数超過など)")
    @ApiResponse(responseCode = "429", description = "YouTube Data API の日次 Quota 枯渇")
    @ApiResponse(responseCode = "502", description = "YouTube Data API の障害")
    fun convert(
        @Valid @RequestBody request: ConvertRequest,
    ): ConvertResponse {
        if (request.inputs.size >
            properties.maxInputs
        ) {
            throw TooManyInputsException(request.inputs.size, properties.maxInputs)
        }
        return ConvertResponse(service.convert(request.inputs).map(ConvertResultDto::from))
    }
}

class TooManyInputsException(
    val actual: Int,
    val max: Int,
) : RuntimeException("too many inputs: $actual (max $max)")
