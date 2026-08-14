package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.VirusScanProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

/**
 * SEC-001：验证 VirusScanner Bean 的条件装配——
 * 默认（未配置/显式 false）→ NoopVirusScanner；app.virus-scan.enabled=true → ClamAvVirusScanner。
 */
class VirusScanConfigTest {

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(ScannerBeans::class.java)

    @Test
    fun `default context uses NoopVirusScanner`() {
        runner.run { ctx ->
            assertThat(ctx).hasSingleBean(NoopVirusScanner::class.java)
            assertThat(ctx).doesNotHaveBean(ClamAvVirusScanner::class.java)
        }
    }

    @Test
    fun `explicit disabled uses NoopVirusScanner`() {
        runner.withPropertyValues("app.virus-scan.enabled=false").run { ctx ->
            assertThat(ctx).hasSingleBean(NoopVirusScanner::class.java)
            assertThat(ctx).doesNotHaveBean(ClamAvVirusScanner::class.java)
        }
    }

    @Test
    fun `enabled context uses ClamAvVirusScanner`() {
        runner.withPropertyValues("app.virus-scan.enabled=true").run { ctx ->
            assertThat(ctx).hasSingleBean(ClamAvVirusScanner::class.java)
            assertThat(ctx).doesNotHaveBean(NoopVirusScanner::class.java)
        }
    }

    @Configuration
    @Import(NoopVirusScanner::class, ClamAvVirusScanner::class)
    @EnableConfigurationProperties(VirusScanProperties::class)
    class ScannerBeans
}
