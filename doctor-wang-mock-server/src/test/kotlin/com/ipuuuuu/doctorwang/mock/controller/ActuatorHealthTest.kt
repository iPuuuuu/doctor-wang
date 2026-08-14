package com.ipuuuuu.doctorwang.mock.controller

import com.ipuuuuu.doctorwang.mock.config.ActuatorHealthTestConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * DEPLOY-001a：/actuator/health 端点暴露且返回 UP（liveness）。
 * DB 健康指示器在测试中禁用（无数据库的上下文）；readiness/DB 探针由部署层处理。
 */
@SpringBootTest(
    classes = [ActuatorHealthTestConfig::class],
    properties = ["management.health.db.enabled=false"],
)
@AutoConfigureMockMvc
class ActuatorHealthTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health endpoint is exposed and reports UP`() {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("UP"))
    }
}
