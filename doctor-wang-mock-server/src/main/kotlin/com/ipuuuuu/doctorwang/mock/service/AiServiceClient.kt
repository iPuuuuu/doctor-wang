package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.config.TraceIdContext
import com.ipuuuuu.doctorwang.mock.model.*
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.util.UUID

class AiServiceUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

interface AiReportClient {
    fun submit(request: AiIngestionRequest, idempotencyKey: String): AiIngestionResponse
    fun status(jobId: String): AiIngestionStatus
    fun retry(jobId: String, requestId: String = UUID.randomUUID().toString()): AiIngestionResponse
    fun query(request: AiRagRequest): AiRagResponse
    fun deleteIndex(namespace: String)
}

@Component
class HttpAiReportClient(private val client: RestClient, private val props: AiServiceProperties) : AiReportClient {
    private fun headers(requestId: String, traceId: String, idempotencyKey: String? = null) = HttpHeaders().apply {
        set("X-Request-Id", requestId); set("X-Trace-Id", traceId)
        if (!idempotencyKey.isNullOrBlank()) set("Idempotency-Key", idempotencyKey)
        if (props.internalToken.isNotBlank()) setBearerAuth(props.internalToken)
        if (props.internalToken.isNotBlank()) set("X-Internal-Token", props.internalToken)
    }
    override fun submit(request: AiIngestionRequest, idempotencyKey: String): AiIngestionResponse = call {
        client.post().uri("/internal/v1/ingestions").headers { it.addAll(headers(request.requestId, request.traceId, idempotencyKey)) }.body(request).retrieve().body(AiIngestionResponse::class.java)!!
    }
    override fun status(jobId: String): AiIngestionStatus = call { client.get().uri("/internal/v1/ingestions/{id}", jobId).headers { it.addAll(headers(UUID.randomUUID().toString(), TraceIdContext.get())) }.retrieve().body(AiIngestionStatus::class.java)!! }
    override fun retry(jobId: String, requestId: String): AiIngestionResponse = call { client.post().uri("/internal/v1/ingestions/{id}/retry", jobId).headers { it.addAll(headers(requestId, TraceIdContext.get(), jobId)) }.retrieve().body(AiIngestionResponse::class.java)!! }
    override fun query(request: AiRagRequest): AiRagResponse = call { client.post().uri("/internal/v1/rag/query").headers { it.addAll(headers(request.requestId, request.traceId)) }.body(request).retrieve().body(AiRagResponse::class.java)!! }
    override fun deleteIndex(namespace: String) { call { client.delete().uri("/internal/v1/indexes/{namespace}", namespace).headers { it.addAll(headers(UUID.randomUUID().toString(), TraceIdContext.get())) }.retrieve().toBodilessEntity(); Unit } }
    private fun <T> call(block: () -> T): T = try { block() } catch (e: Exception) { throw AiServiceUnavailable("AI service unavailable", e) }
}
