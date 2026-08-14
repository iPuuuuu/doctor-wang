package com.ipuuuuu.doctorwang.mock.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.virus-scan")
data class VirusScanProperties(
    /** 是否启用病毒扫描。默认 false（开发态 Noop）；生产必须显式 true（fail-closed）。 */
    val enabled: Boolean = false,
    val host: String = "localhost",
    val port: Int = 3310,
    val connectTimeoutMs: Int = 2_000,
    val readTimeoutMs: Int = 10_000,
) {
    init {
        require(host.isNotBlank()) { "app.virus-scan.host must not be blank" }
        require(port in 1..65535) { "app.virus-scan.port must be between 1 and 65535" }
        require(connectTimeoutMs > 0) { "app.virus-scan.connect-timeout-ms must be positive" }
        require(readTimeoutMs > 0) { "app.virus-scan.read-timeout-ms must be positive" }
    }
}
