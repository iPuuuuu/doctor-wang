package com.ipuuuuu.doctorwang.mock

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class DoctorWangMockServerApplication

fun main(args: Array<String>) {
    runApplication<DoctorWangMockServerApplication>(*args)
}
