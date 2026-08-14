package com.ipuuuuu.doctorwang.mock.config

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.context.annotation.Configuration

/**
 * DEPLOY-001a：仅装配 actuator 自动配置的最小测试上下文。
 * 排除 DataSource/Flyway/JPA（本测试不需要 DB），验证 /actuator/health 暴露与返回。
 * 不扫描应用组件，避免触发仓库 Bean 依赖。
 */
@Configuration
@EnableAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        FlywayAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        JpaRepositoriesAutoConfiguration::class,
    ],
)
class ActuatorHealthTestConfig
