package com.ipuuuuu.doctorwang.mock.service

import com.ipuuuuu.doctorwang.mock.config.AiServiceProperties
import com.ipuuuuu.doctorwang.mock.model.AiIngestionRequest
import com.ipuuuuu.doctorwang.mock.model.AiRagRequest
import org.junit.jupiter.api.Test
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.web.client.RestClient
import org.springframework.http.MediaType
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.junit.jupiter.api.Assertions.assertEquals

class AiServiceClientContractTest {
    @Test
    fun `ingestion submit sends authenticated correlation and idempotency headers`() {
        val builder = RestClient.builder()
        val server = MockRestServiceServer.bindTo(builder).build()
        val client = HttpAiReportClient(builder.baseUrl("http://ai").build(), AiServiceProperties(internalToken = "dev-token", enabled = true))
        server.expect(requestTo("http://ai/internal/v1/ingestions"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(header("X-Request-Id", "req-1"))
            .andExpect(header("X-Trace-Id", "trace-1"))
            .andExpect(header("Idempotency-Key", "job-1"))
            .andExpect(header("X-Internal-Token", "dev-token"))
            .andRespond(withSuccess("{\"jobId\":\"ai-1\",\"status\":\"ACCEPTED\"}", MediaType.APPLICATION_JSON))
        val response = client.submit(AiIngestionRequest("req-1", 1, 2, "report:2:1", "a.pdf", "application/pdf", "2/1/a.pdf", null, 10, "v1", "trace-1"), "job-1")
        server.verify()
        assertEquals("ai-1", response.jobId)
    }

    @Test fun `status retry rag and delete use versioned internal contract`() {
        val builder = RestClient.builder(); val server = MockRestServiceServer.bindTo(builder).build()
        val client = HttpAiReportClient(builder.baseUrl("http://ai").build(), AiServiceProperties(internalToken = "token", enabled = true))
        server.expect(requestTo("http://ai/internal/v1/ingestions/ai-1")).andExpect(method(org.springframework.http.HttpMethod.GET)).andExpect(header("X-Trace-Id", org.hamcrest.Matchers.anything())).andRespond(withSuccess("{\"jobId\":\"ai-1\",\"status\":\"COMPLETED\"}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://ai/internal/v1/ingestions/ai-1/retry")).andExpect(method(org.springframework.http.HttpMethod.POST)).andExpect(header("Idempotency-Key", "ai-1")).andRespond(withSuccess("{\"jobId\":\"ai-1\",\"status\":\"ACCEPTED\"}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://ai/internal/v1/rag/query")).andExpect(method(org.springframework.http.HttpMethod.POST)).andExpect(header("X-Request-Id", "rag-1")).andRespond(withSuccess("{\"requestId\":\"rag-1\",\"status\":\"INSUFFICIENT_EVIDENCE\",\"riskLevel\":\"LOW\",\"summary\":\"资料不足\",\"citations\":[],\"warnings\":[\"资料不足\"],\"traceId\":\"t\"}", MediaType.APPLICATION_JSON))
        server.expect(requestTo("http://ai/internal/v1/indexes/report%3A2%3A1")).andExpect(method(org.springframework.http.HttpMethod.DELETE)).andRespond(withSuccess())
        assertEquals("COMPLETED", client.status("ai-1").status)
        assertEquals("ACCEPTED", client.retry("ai-1", "retry-1").status)
        assertEquals("INSUFFICIENT_EVIDENCE", client.query(AiRagRequest("rag-1", 2, 1, "q", listOf("report:2:1"), traceId = "t")).status)
        client.deleteIndex("report:2:1"); server.verify()
    }
}
