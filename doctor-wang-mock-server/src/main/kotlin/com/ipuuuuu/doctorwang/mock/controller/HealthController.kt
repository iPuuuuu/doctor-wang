package com.ipuuuuu.doctorwang.mock.controller

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController {
    @GetMapping("/")
    fun root(): Map<String, String> {
        return mapOf(
            "service" to "doctor-wang-mock-server",
            "status" to "ok",
        )
    }

    @GetMapping("/healthz")
    fun healthz(): Map<String, String> {
        return mapOf(
            "status" to "ok",
        )
    }
}
