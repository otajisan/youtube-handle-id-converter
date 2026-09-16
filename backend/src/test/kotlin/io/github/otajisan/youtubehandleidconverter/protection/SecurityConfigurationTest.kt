package io.github.otajisan.youtubehandleidconverter.protection

import io.github.otajisan.youtubehandleidconverter.config.AppProperties
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class SecurityConfigurationTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `認証を有効にするのに資格情報が無ければ起動時に失敗する`() {
        val config = SecurityConfiguration(AppProperties(auth = AppProperties.Auth(enabled = true)), mapper)

        assertThatThrownBy { config.userDetailsService() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("APP_AUTH_USERNAME")
    }

    @Test
    fun `Properties の toString は資格情報をマスクする`() {
        val props = AppProperties(auth = AppProperties.Auth(enabled = true, username = "operator", password = "s3cret"))

        assertThat(props.toString()).doesNotContain("operator", "s3cret").contains("****")
    }
}
