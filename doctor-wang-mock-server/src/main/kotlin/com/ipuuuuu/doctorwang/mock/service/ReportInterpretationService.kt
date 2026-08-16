package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.TraceIdContext
import com.ipuuuuu.doctorwang.mock.model.AiRagRequest
import com.ipuuuuu.doctorwang.mock.persistence.entity.MedicalReportEntity
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * 报告解读生成（供处理 executor 与"重新解读"端点共用）：
 * 一次循证 RAG 查询（解读提示词）+ AiSafetyGate 校验 + 三段解析；
 * 格式遵从波动（发现/建议为空）时用更严格提示词重试一次；失败回退诚实兜底文案。
 */
@Service
class ReportInterpretationService(private val client: AiReportClient) {

    fun generate(report: MedicalReportEntity, namespace: String): ReportInterpretation.Interpretation {
        val base = AiRagRequest(
            requestId = UUID.randomUUID().toString(),
            userId = report.user.id,
            reportId = report.id,
            question = ReportInterpretation.PROMPT,
            namespaceHints = listOf(namespace),
            topK = 5,
            traceId = TraceIdContext.get(),
        )
        return try {
            val response = AiSafetyGate.validate(client.query(base), report.id)
            if (response.status != "OK") return ReportInterpretation.Interpretation.fallback()
            var parsed = ReportInterpretation.parse(response.summary)
            if (parsed.findings.isEmpty() && parsed.suggestions.isEmpty()) {
                val retry = base.copy(
                    requestId = UUID.randomUUID().toString(),
                    question = ReportInterpretation.PROMPT + "\n严格检查输出包含【摘要】【重点发现】【建议】三个标题，缺一不可。",
                )
                val retried = AiSafetyGate.validate(client.query(retry), report.id)
                if (retried.status == "OK") parsed = ReportInterpretation.parse(retried.summary)
            }
            parsed
        } catch (_: Exception) {
            ReportInterpretation.Interpretation.fallback()
        }
    }
}
