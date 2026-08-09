package com.ipuuuuu.doctorwang.mock.service

import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile

interface VirusScanner { fun scan(file: MultipartFile): ScanResult }
data class ScanResult(val clean: Boolean, val reason: String? = null)

/** Development-only seam. Production must replace this with an ICAP/ClamAV or managed scanner. */
@Component
class NoopVirusScanner : VirusScanner { override fun scan(file: MultipartFile) = ScanResult(clean = true) }
