package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.VirusScanProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 生产态 ClamAV clamd 客户端（SEC-001，ADR-0008 配套）。
 *
 * 协议：`zINSTREAM\0` + 2 字节大端长度前缀分块 + 0 长度终止块；
 * 响应 `stream: OK`（干净）或 `stream: <病毒名> FOUND`（检出）。
 *
 * FAIL-CLOSED（用户已确认）：任何连接失败、超时、IO 错误或畸形响应
 * 一律返回 clean=false + SCANNER_UNAVAILABLE，由上层拒绝上传，
 * 绝不默认放行。
 */
@Component
@ConditionalOnProperty(name = ["app.virus-scan.enabled"], havingValue = "true")
class ClamAvVirusScanner(
    private val props: VirusScanProperties,
) : VirusScanner {

    override fun scan(file: MultipartFile): ScanResult = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(props.host, props.port), props.connectTimeoutMs)
            socket.soTimeout = props.readTimeoutMs
            val out = BufferedOutputStream(socket.getOutputStream())
            out.write(INSTREAM_COMMAND.toByteArray(Charsets.US_ASCII))
            out.write(0)
            file.inputStream.use { input ->
                val buf = ByteArray(CHUNK_SIZE)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    if (n > 0) {
                        out.write(n shr 8 and 0xFF)
                        out.write(n and 0xFF)
                        out.write(buf, 0, n)
                    }
                }
            }
            out.write(0)
            out.write(0)
            out.flush()
            val response = readResponse(socket)
            when {
                response == null -> ScanResult(clean = false, reason = ScanReasons.SCANNER_UNAVAILABLE)
                RESPONSE_CLEAN in response -> ScanResult(clean = true)
                RESPONSE_FOUND in response -> ScanResult(clean = false, reason = ScanReasons.MALWARE)
                else -> ScanResult(clean = false, reason = ScanReasons.SCANNER_UNAVAILABLE)
            }
        }
    } catch (e: Exception) {
        // fail-closed：扫描器不可达/异常时拒绝，而不是放行。
        ScanResult(clean = false, reason = ScanReasons.SCANNER_UNAVAILABLE)
    }

    private fun readResponse(socket: Socket): String? {
        val input = socket.getInputStream()
        val sb = StringBuilder()
        var b = input.read()
        while (b != -1 && b != 0 && b != '\n'.code) {
            sb.append(b.toChar())
            b = input.read()
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private companion object {
        const val INSTREAM_COMMAND = "zINSTREAM"
        const val CHUNK_SIZE = 8192
        const val RESPONSE_CLEAN = "OK"
        const val RESPONSE_FOUND = "FOUND"
    }
}
