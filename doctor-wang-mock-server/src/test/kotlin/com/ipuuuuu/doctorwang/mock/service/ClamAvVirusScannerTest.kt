package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.VirusScanProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class ClamAvVirusScannerTest {

    private val pdfBytes = byteArrayOf(0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34) // %PDF-1.4

    @Test
    fun `clean response passes`() {
        withClamdStub("stream: OK") { port ->
            val scanner = scanner(props(port = port))
            val result = scanner.scan(MockMultipartFile("file", "a.pdf", "application/pdf", pdfBytes))
            assertTrue(result.clean)
            assertEquals(null, result.reason)
        }
    }

    @Test
    fun `found response is malware`() {
        withClamdStub("stream: Eicar-Test-Signature FOUND") { port ->
            val scanner = scanner(props(port = port))
            val result = scanner.scan(MockMultipartFile("file", "a.pdf", "application/pdf", pdfBytes))
            assertFalse(result.clean)
            assertEquals(ScanReasons.MALWARE, result.reason)
        }
    }

    @Test
    fun `connection refused fails closed`() {
        // 找一个必然空闲的端口：先开一个 ServerSocket 拿到端口再关闭。
        val freePort = ServerSocket(0).use { it.localPort }
        val scanner = scanner(props(port = freePort, connectTimeoutMs = 500))
        val result = scanner.scan(MockMultipartFile("file", "a.pdf", "application/pdf", pdfBytes))
        assertFalse(result.clean)
        assertEquals(ScanReasons.SCANNER_UNAVAILABLE, result.reason)
    }

    @Test
    fun `server closes without response fails closed`() {
        withClamdStub(null) { port ->
            val scanner = scanner(props(port = port, readTimeoutMs = 2_000))
            val result = scanner.scan(MockMultipartFile("file", "a.pdf", "application/pdf", pdfBytes))
            assertFalse(result.clean)
            assertEquals(ScanReasons.SCANNER_UNAVAILABLE, result.reason)
        }
    }

    @Test
    fun `malformed response fails closed`() {
        withClamdStub("garbage-no-keywords") { port ->
            val scanner = scanner(props(port = port))
            val result = scanner.scan(MockMultipartFile("file", "a.pdf", "application/pdf", pdfBytes))
            assertFalse(result.clean)
            assertEquals(ScanReasons.SCANNER_UNAVAILABLE, result.reason)
        }
    }

    @Test
    fun `slow server times out and fails closed`() {
        val server = ServerSocket(0)
        val accepted = CountDownLatch(1)
        thread(isDaemon = true) {
            server.accept().use { sock ->
                accepted.countDown()
                // 不返回任何响应，让客户端读超时
                Thread.sleep(2_000)
            }
        }
        try {
            val scanner = scanner(props(port = server.localPort, readTimeoutMs = 300))
            val result = scanner.scan(MockMultipartFile("file", "a.pdf", "application/pdf", pdfBytes))
            assertFalse(result.clean)
            assertEquals(ScanReasons.SCANNER_UNAVAILABLE, result.reason)
        } finally {
            server.close()
        }
    }

    // ---- helpers ----

    private fun scanner(props: VirusScanProperties) = ClamAvVirusScanner(props)

    private fun props(port: Int, connectTimeoutMs: Int = 1_000, readTimeoutMs: Int = 2_000) =
        VirusScanProperties(enabled = true, host = "127.0.0.1", port = port, connectTimeoutMs = connectTimeoutMs, readTimeoutMs = readTimeoutMs)

    /**
     * 启动一个 clamd 协议桩：读取客户端 INSTREAM 直到 0x00 0x00 终止块，
     * 然后返回 [response]（null 表示直接关闭连接，模拟无响应）。
     */
    private fun withClamdStub(response: String?, block: (Int) -> Unit) {
        val server = ServerSocket(0)
        val served = CountDownLatch(1)
        thread(isDaemon = true) {
            server.accept().use { sock ->
                val input = sock.getInputStream()
                var prev = -1
                while (true) {
                    val b = input.read()
                    if (b == -1) break
                    if (prev == 0 && b == 0) break
                    prev = b
                }
                if (response != null) {
                    sock.getOutputStream().use { out ->
                        out.write((response + "\u0000").toByteArray(Charsets.US_ASCII))
                        out.flush()
                    }
                }
                served.countDown()
            }
        }
        try {
            block(server.localPort)
        } finally {
            server.close()
            served.await(3, TimeUnit.SECONDS)
        }
    }
}
