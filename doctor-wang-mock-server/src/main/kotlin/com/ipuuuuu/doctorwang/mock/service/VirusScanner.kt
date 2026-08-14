package com.ipuuuuu.doctorwang.mock.service

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

interface VirusScanner { fun scan(file: MultipartFile): ScanResult }
data class ScanResult(val clean: Boolean, val reason: String? = null)

/** 扫描结果原因码：ReportProcessingService 据此区分"检出恶意文件"与"扫描服务不可用"（fail-closed）。 */
object ScanReasons {
    const val MALWARE = "MALWARE"
    const val SCANNER_UNAVAILABLE = "SCANNER_UNAVAILABLE"
}

/**
 * 开发态占位实现（SEC-001）：无条件放行。
 * 仅当 `app.virus-scan.enabled=false`（默认）时生效；生产必须设置
 * `app.virus-scan.enabled=true` 以激活 ClamAvVirusScanner（fail-closed），
 * 否则上传不会被病毒扫描，属于已知生产缺口。
 */
@Component
@ConditionalOnProperty(name = ["app.virus-scan.enabled"], havingValue = "false", matchIfMissing = true)
class NoopVirusScanner : VirusScanner {
    override fun scan(file: MultipartFile) = ScanResult(clean = true)
}
