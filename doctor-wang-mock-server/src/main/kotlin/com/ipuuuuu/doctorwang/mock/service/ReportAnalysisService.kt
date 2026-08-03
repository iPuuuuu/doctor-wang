package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.model.ReportAnalysisResult
import org.springframework.stereotype.Service

@Service
class ReportAnalysisService {
    fun analyze(extractedText: String, mediaType: String, title: String): ReportAnalysisResult {
        val normalized = extractedText.lowercase()
        val findings = buildList {
            if (normalized.contains("脂肪肝") || title.contains("肝", ignoreCase = true)) {
                add("存在肝胆相关检查线索，建议结合肝功能结果综合判断。")
            }
            if (mediaType == "application/pdf") {
                add("当前报告来自 PDF 文件，已完成演示版文本解析。")
            } else {
                add("当前报告来自图片文件，已完成演示版 OCR 占位分析。")
            }
            if (normalized.contains("胃") || title.contains("胃", ignoreCase = true)) {
                add("文本中包含胃部相关线索，建议结合症状持续时间继续观察。")
            }
        }.ifEmpty {
            listOf("暂未识别出明确异常线索，当前结果为演示版自动分析。")
        }

        val suggestions = buildList {
            add("建议结合原始报告和线下医生意见共同判断。")
            add("如症状持续或加重，请尽快线下复诊。")
            if (mediaType != "application/pdf") {
                add("如需更高准确度，可补充上传更清晰的扫描件或 PDF。")
            }
        }

        return ReportAnalysisResult(
            summary = "已完成演示版 AI 报告分析，可查看重点发现与后续建议。",
            findings = findings,
            suggestions = suggestions,
        )
    }
}
