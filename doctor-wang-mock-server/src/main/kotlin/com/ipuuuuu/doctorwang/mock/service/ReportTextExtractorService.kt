package com.ipuuuuu.doctorwang.mock.service

import java.nio.file.Files
import java.nio.file.Path
import java.nio.charset.StandardCharsets
import org.springframework.stereotype.Service

@Service
class ReportTextExtractorService {
    fun extract(path: Path, mediaType: String, originalName: String): String {
        return when (mediaType) {
            "application/pdf" -> buildPdfPlaceholder(path, originalName)
            "image/png", "image/jpeg" -> buildImagePlaceholder(path, originalName)
            else -> "暂不支持该文件类型的文本提取：$originalName"
        }
    }

    private fun buildPdfPlaceholder(path: Path, originalName: String): String {
        val size = Files.size(path)
        return "PDF 报告：$originalName。当前为演示版文本抽取，文件大小 ${size} bytes，后续可接入真实 PDF 文本解析。"
    }

    private fun buildImagePlaceholder(path: Path, originalName: String): String {
        val size = Files.size(path)
        val preview = runCatching {
            Files.readAllBytes(path)
                .take(48)
                .toByteArray()
                .toString(StandardCharsets.ISO_8859_1)
                .replace(Regex("[^\\p{Print}]"), " ")
                .trim()
        }.getOrDefault("")
        return "图片报告：$originalName。当前为演示版 OCR，占位识别到的片段：${preview.ifBlank { "未识别到可展示内容" }}。文件大小 ${size} bytes。"
    }
}
