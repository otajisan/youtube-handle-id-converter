package io.github.otajisan.youtubehandleidconverter.conversion

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class InputParserTest {
    private val parser = InputParser()

    @ParameterizedTest
    @CsvSource(
        "@example, example",
        "example, example",
        "  @Example.Name_1-2  , Example.Name_1-2",
        "https://www.youtube.com/@example, example",
        "https://youtube.com/@example/videos, example",
        "http://m.youtube.com/@example?si=abc, example",
        "youtube.com/@example, example",
        "YOUTUBE.COM/@example, example",
    )
    fun `ハンドルとして解釈する`(
        raw: String,
        expected: String,
    ) {
        assertThat(parser.parse(raw)).isEqualTo(ParsedInput.Handle(expected))
    }

    @ParameterizedTest
    @CsvSource(
        "UC-9-kyTW8ZkZNDHQJ6FgpwQ, UC-9-kyTW8ZkZNDHQJ6FgpwQ",
        "https://www.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ, UC-9-kyTW8ZkZNDHQJ6FgpwQ",
        "https://www.youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ/about, UC-9-kyTW8ZkZNDHQJ6FgpwQ",
        "youtube.com/channel/UC-9-kyTW8ZkZNDHQJ6FgpwQ, UC-9-kyTW8ZkZNDHQJ6FgpwQ",
    )
    fun `Channel ID として解釈する(ハンドルの正規表現にも一致するが ID を優先する)`(
        raw: String,
        expected: String,
    ) {
        assertThat(parser.parse(raw)).isEqualTo(ParsedInput.ChannelId(expected))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "   ",
            "ab",
            "@ab",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "has space",
            "日本語",
            "https://example.com/@example",
            "https://www.youtube.com/watch?v=abc",
            "https://www.youtube.com/",
            "https://www.youtube.com/channel/notachannelid",
            "https://www.youtube.com/@",
            "youtube.com/@a b",
        ],
    )
    fun `解釈できない入力は Invalid`(raw: String) {
        assertThat(parser.parse(raw)).isInstanceOf(ParsedInput.Invalid::class.java)
    }

    @org.junit.jupiter.api.Test
    fun `長すぎる入力は Invalid`() {
        assertThat(
            parser.parse("a".repeat(InputParser.MAX_INPUT_LENGTH + 1)),
        ).isEqualTo(ParsedInput.Invalid("too long"))
    }
}
