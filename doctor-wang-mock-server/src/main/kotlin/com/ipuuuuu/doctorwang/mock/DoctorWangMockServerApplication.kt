package com.ipuuuuu.doctorwang.mock

import com.ipuuuuu.doctorwang.mock.config.AuthProperties
import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.config.VirusScanProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AuthProperties::class, AiServiceProperties::class, VirusScanProperties::class)
class DoctorWangMockServerApplication

fun main(args: Array<String>) {
    runApplication<DoctorWangMockServerApplication>(*args)
}
