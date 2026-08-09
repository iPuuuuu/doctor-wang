package com.ipuuuuu.doctorwang.mock.config

import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {
    @Bean
    fun aiRestClient(builder: RestClient.Builder, props: AiServiceProperties): RestClient =
        builder.baseUrl(props.baseUrl).build()
}
