# Doctor-Wang Full-Stack Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stabilize Doctor-Wang’s full-stack architecture by freezing cross-service contracts, hardening the Spring Boot backend foundation, aligning the AI service boundary, updating the Android client to the stable contract, and adding QA gates for safe iterative delivery.

**Architecture:** Keep the current three-tier shape: Android app → Spring Boot API → Python AI service. Implement in contract-first order so each subsystem can be tested independently: first backend/API/error-model foundations, then AI-service integration and safety boundaries, then Android consumption of the stabilized contract, then QA verification across the end-to-end paths.

**Tech Stack:** Android (Kotlin, Jetpack Compose, ViewModel, Repository, Hilt, Retrofit, Room/DataStore), Backend (Spring Boot 3, Kotlin/JVM 17, PostgreSQL, Flyway, JPA), AI Service (Python, FastAPI, LangGraph-style pipeline modules, pytest)

## Global Constraints

- Do not implement before design approval.
- Do not present mock behavior as production AI capability.
- Android must not call model providers directly.
- Do not write passwords, tokens, health data, or report contents to logs or the repository.
- Any database schema change must use a new Flyway migration.
- Any medical, security, privacy, API, or database change must explicitly document risk.
- Do not add unnecessary dependencies or refactor unrelated modules.
- Keep backend on Spring Boot 3 + Kotlin/JVM 17 unless a separate approved Java 21 migration plan exists.
- Preserve traceability with request IDs / trace IDs across backend and AI-service boundaries.

---

## File Structure Map

### Backend files likely to change

- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/ApiModels.kt`
  - Shared API response envelopes and error body types.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/ReportApiModels.kt`
  - Report/upload/status DTOs returned to Android.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/AiServiceModels.kt`
  - Internal DTOs exchanged with `ai-service`.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/ApiExceptions.kt`
  - Exception types mapped to error codes.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/GlobalExceptionHandler.kt`
  - Unified error envelope + trace ID serialization.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/controller/ReportV1Controller.kt`
  - Stable report upload/status/detail endpoints.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/controller/ChatController.kt`
  - Stable chat response shape and safety warning surfacing.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/AiServiceClient.kt`
  - AI-service contract calls and response mapping.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportProcessingService.kt`
  - Upload orchestration and job lifecycle.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportJobStateMachine.kt`
  - Allowed report-processing states.
- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/TraceIdFilter.kt`
  - Trace ID propagation.
- `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/service/AiServiceClientContractTest.kt`
  - Backend ↔ AI-service contract tests.
- `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportJobStateMachineTest.kt`
  - Report state transition tests.
- `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/integration/PostgresIntegrationMatrixTest.kt`
  - Integration coverage for persistence-backed flows.
- `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/controller/ReportV1ControllerTest.kt`
  - Create if absent; API response contract tests.
- `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/controller/ChatControllerTest.kt`
  - Create if absent; chat response contract tests.

### AI service files likely to change

- `ai-service/app/schemas.py`
  - Request/response schemas for ingestion and retrieval.
- `ai-service/app/main.py`
  - FastAPI routes.
- `ai-service/app/graph.py`
  - Ingestion/retrieval orchestration entry points.
- `ai-service/app/jobs.py`
  - Job persistence and status transitions.
- `ai-service/app/rag.py`
  - Retrieval response assembly and citations.
- `ai-service/app/safety.py`
  - Medical-risk gating and refusal/fallback shaping.
- `ai-service/tests/test_pipeline.py`
  - Current pipeline tests; expand for response contract and failure modes.
- `ai-service/tests/test_api_contract.py`
  - Create; route-level contract tests.
- `ai-service/tests/test_safety.py`
  - Create; high-risk triage tests.

### Android files likely to change

- `app/src/main/java/com/ipuuuuu/doctorwang/data/api/DoctorWangApi.kt`
  - Retrofit interface and DTO alignment.
- `app/src/main/java/com/ipuuuuu/doctorwang/data/model/Message.kt`
  - Chat message warnings / citations / metadata.
- `app/src/main/java/com/ipuuuuu/doctorwang/data/model/MedicalReport.kt`
  - Report processing/status representation.
- `app/src/main/java/com/ipuuuuu/doctorwang/data/repository/DoctorWangRepository.kt`
  - Repository contract.
- `app/src/main/java/com/ipuuuuu/doctorwang/data/repository/RemoteDoctorWangRepository.kt`
  - API mapping and error handling.
- `app/src/main/java/com/ipuuuuu/doctorwang/storage/SessionManager.kt`
  - Session/token persistence boundaries.
- `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/ChatViewModel.kt`
  - Chat state, warnings, and failure UI state.
- `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/UploadViewModel.kt`
  - Upload lifecycle and job status polling.
- `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/ReportDetailViewModel.kt`
  - Report detail / processing state handling.
- `app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/chat/ChatScreen.kt`
  - Render warnings/citations safely.
- `app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/upload/UploadScreen.kt`
  - Render upload/processing states.
- `app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/report/ReportDetailScreen.kt`
  - Render report status and retry/failure states.
- `app/src/main/java/com/ipuuuuu/doctorwang/ui/navigation/AppNavGraph.kt`
  - Any route-level state wiring required by contract changes.
- `app/src/test/java/com/ipuuuuu/doctorwang/data/repository/RemoteDoctorWangRepositoryTest.kt`
  - Create if absent; repository mapping tests.
- `app/src/test/java/com/ipuuuuu/doctorwang/viewmodel/ChatViewModelTest.kt`
  - Create if absent; chat state tests.
- `app/src/test/java/com/ipuuuuu/doctorwang/viewmodel/UploadViewModelTest.kt`
  - Create if absent; upload state tests.

### Documentation files likely to change

- `docs/technical-decisions.md`
  - Record finalized contract/error-model decisions.
- `docs/roadmap.md`
  - Reflect completed phase progress if scope advances.
- `doctor-wang-mock-server/README.md`
  - Backend run/test notes if commands or env vars change.
- `ai-service/README.md`
  - AI-service contract/run/test notes.
- `README.md`
  - Top-level architecture and module responsibilities if materially changed.

---

### Task 1: Freeze Backend Public API and Error Envelope

**Files:**
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/ApiModels.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/ReportApiModels.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/ApiExceptions.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/GlobalExceptionHandler.kt`
- Test: `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/controller/ReportV1ControllerTest.kt`
- Test: `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/controller/ChatControllerTest.kt`

**Interfaces:**
- Consumes: existing Spring controllers and exception types.
- Produces:
  - `data class ApiErrorResponse(val code: String, val message: String, val traceId: String?)`
  - `data class ReportStatusResponse(val reportId: Long, val status: String, val phase: String?, val canRetry: Boolean, val requiresReview: Boolean, val failureCategory: String?, val traceId: String?)`
  - `data class ChatReplyResponse(val message: String, val warnings: List<String>, val citations: List<ChatCitationResponse>, val traceId: String?)`

- [ ] **Step 1: Write the failing report-controller contract test**

```kotlin
@Test
fun `get report status returns stable contract with trace id`() {
    mockMvc.perform(get("/api/v1/reports/10002/status").header("X-Trace-Id", "trace-test"))
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.reportId").value(10002))
        .andExpect(jsonPath("$.status").isString)
        .andExpect(jsonPath("$.canRetry").isBoolean)
        .andExpect(jsonPath("$.requiresReview").isBoolean)
        .andExpect(jsonPath("$.traceId").value("trace-test"))
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && ./gradlew test --tests '*ReportV1ControllerTest'`
Expected: FAIL because the response shape or test file does not exist yet.

- [ ] **Step 3: Write the failing chat-controller contract test**

```kotlin
@Test
fun `chat reply exposes warnings citations and trace id`() {
    val request = """{"message":"胸痛怎么办"}"""

    mockMvc.perform(
        post("/api/v1/chat")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Trace-Id", "trace-chat")
            .content(request)
    )
        .andExpect(status().isOk)
        .andExpect(jsonPath("$.message").isString)
        .andExpect(jsonPath("$.warnings").isArray)
        .andExpect(jsonPath("$.citations").isArray)
        .andExpect(jsonPath("$.traceId").value("trace-chat"))
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && ./gradlew test --tests '*ChatControllerTest'`
Expected: FAIL because the response does not yet match the stable contract.

- [ ] **Step 5: Implement the minimal DTOs and exception envelope**

```kotlin
data class ApiErrorResponse(
    val code: String,
    val message: String,
    val traceId: String?
)

data class ReportStatusResponse(
    val reportId: Long,
    val status: String,
    val phase: String?,
    val canRetry: Boolean,
    val requiresReview: Boolean,
    val failureCategory: String?,
    val traceId: String?
)

data class ChatCitationResponse(
    val sourceType: String,
    val sourceId: String,
    val pageNo: Int?,
    val excerpt: String,
    val confidence: Double?
)

data class ChatReplyResponse(
    val message: String,
    val warnings: List<String>,
    val citations: List<ChatCitationResponse>,
    val traceId: String?
)
```

- [ ] **Step 6: Map exceptions to the new envelope**

```kotlin
@ExceptionHandler(ApiException::class)
fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
    val traceId = request.getHeader("X-Trace-Id")
    return ResponseEntity.status(ex.status).body(
        ApiErrorResponse(
            code = ex.code,
            message = ex.message ?: ex.code,
            traceId = traceId,
        )
    )
}
```

- [ ] **Step 7: Run the targeted tests**

Run: `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && ./gradlew test --tests '*ReportV1ControllerTest' --tests '*ChatControllerTest'`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/ApiModels.kt \
  doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/ReportApiModels.kt \
  doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/ApiExceptions.kt \
  doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/GlobalExceptionHandler.kt \
  doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/controller/ReportV1ControllerTest.kt \
  doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/controller/ChatControllerTest.kt

git commit -m "feat: freeze backend api response contract"
```

### Task 2: Harden Report Processing State and Backend↔AI Internal Contract

**Files:**
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/AiServiceModels.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/AiServiceClient.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportProcessingService.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportJobStateMachine.kt`
- Test: `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/service/AiServiceClientContractTest.kt`
- Test: `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportJobStateMachineTest.kt`

**Interfaces:**
- Consumes: `ReportStatusResponse` from Task 1.
- Produces:
  - `data class AiIngestionSubmitRequest(...)`
  - `data class AiIngestionStatusResponse(val jobId: String, val reportId: Long, val status: String, val phase: String?, val canRetry: Boolean, val requiresReview: Boolean, val failureCategory: String?, val resultVersion: String?, val traceId: String?)`
  - `fun ReportJobStateMachine.transition(current: String, event: String): String`

- [ ] **Step 1: Write the failing AI-service contract test**

```kotlin
@Test
fun `submit ingestion sends stable payload to ai service`() {
    val request = AiIngestionSubmitRequest(
        requestId = "req_123",
        reportId = 10002,
        userId = 9001,
        namespace = "report:9001:10002",
        objectKey = "reports/10002/original.pdf",
        fileName = "exam.pdf",
        contentType = "application/pdf",
        fileSize = 123456,
        sourceVersion = "upload-v1",
        traceId = "trace_abc",
    )

    val response = aiServiceClient.submitIngestion(request)

    assertEquals("ACCEPTED", response.status)
    assertEquals("job_001", response.jobId)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && ./gradlew test --tests '*AiServiceClientContractTest'`
Expected: FAIL because the request/response types or mapping are incomplete.

- [ ] **Step 3: Write the failing state-machine transition test**

```kotlin
@Test
fun `job moves from indexing to review required on low confidence`() {
    val next = stateMachine.transition(current = "INDEXING", event = "LOW_CONFIDENCE")
    assertEquals("REVIEW_REQUIRED", next)
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && ./gradlew test --tests '*ReportJobStateMachineTest'`
Expected: FAIL because the transition is not defined.

- [ ] **Step 5: Implement the internal DTOs**

```kotlin
data class AiIngestionSubmitRequest(
    val requestId: String,
    val reportId: Long,
    val userId: Long,
    val namespace: String,
    val objectKey: String,
    val fileName: String,
    val contentType: String,
    val fileSize: Long,
    val sourceVersion: String,
    val traceId: String,
)

data class AiIngestionStatusResponse(
    val jobId: String,
    val reportId: Long,
    val status: String,
    val phase: String?,
    val canRetry: Boolean,
    val requiresReview: Boolean,
    val failureCategory: String?,
    val resultVersion: String?,
    val traceId: String?,
)
```

- [ ] **Step 6: Add the missing state transition logic**

```kotlin
fun transition(current: String, event: String): String = when (current to event) {
    "INDEXING" to "LOW_CONFIDENCE" -> "REVIEW_REQUIRED"
    "INDEXING" to "INDEX_COMPLETE" -> "ANALYZING"
    "ANALYZING" to "SAFETY_FLAGGED" -> "REVIEW_REQUIRED"
    "ANALYZING" to "ANALYSIS_COMPLETE" -> "COMPLETED"
    else -> error("Unsupported transition: $current -> $event")
}
```

- [ ] **Step 7: Run the targeted tests**

Run: `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && ./gradlew test --tests '*AiServiceClientContractTest' --tests '*ReportJobStateMachineTest'`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/AiServiceModels.kt \
  doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/AiServiceClient.kt \
  doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportProcessingService.kt \
  doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportJobStateMachine.kt \
  doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/service/AiServiceClientContractTest.kt \
  doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportJobStateMachineTest.kt

git commit -m "feat: align report processing with ai contract"
```

### Task 3: Align AI Service Route Schemas and Safety Output

**Files:**
- Modify: `ai-service/app/schemas.py`
- Modify: `ai-service/app/main.py`
- Modify: `ai-service/app/graph.py`
- Modify: `ai-service/app/rag.py`
- Modify: `ai-service/app/safety.py`
- Test: `ai-service/tests/test_api_contract.py`
- Test: `ai-service/tests/test_safety.py`
- Test: `ai-service/tests/test_pipeline.py`

**Interfaces:**
- Consumes: `AiIngestionSubmitRequest` and `AiIngestionStatusResponse` names from Task 2.
- Produces:
  - `class IngestionSubmitRequest(BaseModel)`
  - `class IngestionSubmitResponse(BaseModel)`
  - `class RagQueryRequest(BaseModel)`
  - `class RagQueryResponse(BaseModel)`
  - `def evaluate_medical_risk(question: str) -> tuple[str, list[str], bool]`

- [ ] **Step 1: Write the failing ingestion-route contract test**

```python
def test_ingestion_submit_contract(client):
    response = client.post(
        "/internal/v1/ingestions",
        json={
            "requestId": "req_123",
            "reportId": 10002,
            "userId": 9001,
            "namespace": "report:9001:10002",
            "objectKey": "reports/10002/original.pdf",
            "fileName": "exam.pdf",
            "contentType": "application/pdf",
            "fileSize": 123456,
            "sourceVersion": "upload-v1",
            "traceId": "trace_abc",
        },
    )
    body = response.json()
    assert response.status_code == 202
    assert body["status"] == "ACCEPTED"
    assert "jobId" in body
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang/ai-service && pytest tests/test_api_contract.py::test_ingestion_submit_contract -v`
Expected: FAIL because the route or payload does not match exactly.

- [ ] **Step 3: Write the failing medical-risk safety test**

```python
def test_high_risk_question_returns_triage_warning():
    risk_level, warnings, blocked = evaluate_medical_risk("我胸痛并且呼吸困难怎么办")
    assert risk_level == "HIGH"
    assert blocked is True
    assert any("就医" in warning or "急诊" in warning for warning in warnings)
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang/ai-service && pytest tests/test_safety.py::test_high_risk_question_returns_triage_warning -v`
Expected: FAIL because the safety helper or output shape is missing.

- [ ] **Step 5: Implement the schema classes**

```python
class IngestionSubmitRequest(BaseModel):
    requestId: str
    reportId: int
    userId: int
    namespace: str
    objectKey: str
    fileName: str
    contentType: str
    fileSize: int
    sourceVersion: str
    traceId: str

class IngestionSubmitResponse(BaseModel):
    jobId: str
    status: str
    acceptedAt: str

class RagQueryResponse(BaseModel):
    requestId: str
    status: str
    riskLevel: str
    summary: str
    citations: list[dict]
    warnings: list[str]
    needsReview: bool
    traceId: str
```

- [ ] **Step 6: Implement the safety helper and response shaping**

```python
def evaluate_medical_risk(question: str) -> tuple[str, list[str], bool]:
    high_risk_terms = ["胸痛", "呼吸困难", "意识改变", "严重出血", "自伤"]
    if any(term in question for term in high_risk_terms):
        return (
            "HIGH",
            ["检测到高风险症状，请尽快线下就医或联系急诊服务。", "本系统不能替代医生诊断。"],
            True,
        )
    return ("LOW", ["仅供健康辅助参考，不构成诊断意见"], False)
```

- [ ] **Step 7: Run the targeted tests**

Run: `cd /Users/a1234/code/doctor-wang/ai-service && pytest tests/test_api_contract.py tests/test_safety.py tests/test_pipeline.py -v`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add ai-service/app/schemas.py ai-service/app/main.py ai-service/app/graph.py \
  ai-service/app/rag.py ai-service/app/safety.py \
  ai-service/tests/test_api_contract.py ai-service/tests/test_safety.py ai-service/tests/test_pipeline.py

git commit -m "feat: stabilize ai service contract and safety output"
```

### Task 4: Wire Android Data Models and Repository to the Stable Contract

**Files:**
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/data/api/DoctorWangApi.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/data/model/Message.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/data/model/MedicalReport.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/data/repository/DoctorWangRepository.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/data/repository/RemoteDoctorWangRepository.kt`
- Test: `app/src/test/java/com/ipuuuuu/doctorwang/data/repository/RemoteDoctorWangRepositoryTest.kt`

**Interfaces:**
- Consumes:
  - `ChatReplyResponse` from Task 1
  - `ReportStatusResponse` from Task 1
- Produces:
  - `data class Message(val id: String, val role: String, val content: String, val warnings: List<String>, val citations: List<MessageCitation>)`
  - `data class MedicalReport(val id: Long, val title: String, val status: String, val phase: String?, val canRetry: Boolean, val requiresReview: Boolean, val failureCategory: String?)`
  - `suspend fun DoctorWangRepository.getReportStatus(reportId: Long): MedicalReport`

- [ ] **Step 1: Write the failing repository mapping test for chat replies**

```kotlin
@Test
fun `send message maps warnings and citations from api`() = runTest {
    val reply = repository.sendMessage("这个报告需要注意什么？")

    assertEquals("assistant", reply.role)
    assertEquals(listOf("仅供健康辅助参考，不构成诊断意见"), reply.warnings)
    assertEquals(1, reply.citations.size)
    assertEquals("report", reply.citations.first().sourceType)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang && ./gradlew test --tests '*RemoteDoctorWangRepositoryTest'`
Expected: FAIL because the repository or model does not yet carry warnings/citations.

- [ ] **Step 3: Write the failing repository mapping test for report status**

```kotlin
@Test
fun `get report status maps processing fields`() = runTest {
    val report = repository.getReportStatus(10002)

    assertEquals("INDEXING", report.phase)
    assertEquals(false, report.requiresReview)
    assertEquals(true, report.canRetry)
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang && ./gradlew test --tests '*RemoteDoctorWangRepositoryTest'`
Expected: FAIL because the status fields are absent or incorrectly mapped.

- [ ] **Step 5: Implement the Android data models**

```kotlin
data class MessageCitation(
    val sourceType: String,
    val sourceId: String,
    val pageNo: Int?,
    val excerpt: String,
    val confidence: Double?,
)

data class Message(
    val id: String,
    val role: String,
    val content: String,
    val warnings: List<String> = emptyList(),
    val citations: List<MessageCitation> = emptyList(),
)

data class MedicalReport(
    val id: Long,
    val title: String,
    val status: String,
    val phase: String? = null,
    val canRetry: Boolean = false,
    val requiresReview: Boolean = false,
    val failureCategory: String? = null,
)
```

- [ ] **Step 6: Add the repository API and mapping**

```kotlin
interface DoctorWangRepository {
    suspend fun getReportStatus(reportId: Long): MedicalReport
}

override suspend fun getReportStatus(reportId: Long): MedicalReport {
    val response = api.getReportStatus(reportId)
    return MedicalReport(
        id = response.reportId,
        title = "Report #${response.reportId}",
        status = response.status,
        phase = response.phase,
        canRetry = response.canRetry,
        requiresReview = response.requiresReview,
        failureCategory = response.failureCategory,
    )
}
```

- [ ] **Step 7: Run the targeted test**

Run: `cd /Users/a1234/code/doctor-wang && ./gradlew test --tests '*RemoteDoctorWangRepositoryTest'`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ipuuuuu/doctorwang/data/api/DoctorWangApi.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/data/model/Message.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/data/model/MedicalReport.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/data/repository/DoctorWangRepository.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/data/repository/RemoteDoctorWangRepository.kt \
  app/src/test/java/com/ipuuuuu/doctorwang/data/repository/RemoteDoctorWangRepositoryTest.kt

git commit -m "feat: align android repository with backend contract"
```

### Task 5: Update Android ViewModels and UI for Warnings and Processing States

**Files:**
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/ChatViewModel.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/UploadViewModel.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/ReportDetailViewModel.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/chat/ChatScreen.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/upload/UploadScreen.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/report/ReportDetailScreen.kt`
- Test: `app/src/test/java/com/ipuuuuu/doctorwang/viewmodel/ChatViewModelTest.kt`
- Test: `app/src/test/java/com/ipuuuuu/doctorwang/viewmodel/UploadViewModelTest.kt`

**Interfaces:**
- Consumes: `Message`, `MessageCitation`, and `MedicalReport` from Task 4.
- Produces:
  - `data class ChatUiState(..., val latestWarnings: List<String> = emptyList())`
  - `data class UploadUiState(..., val reportPhase: String? = null, val canRetry: Boolean = false, val failureCategory: String? = null)`
  - UI rendering of warnings, citations, and processing state labels.

- [ ] **Step 1: Write the failing chat-viewmodel test**

```kotlin
@Test
fun `assistant warnings are exposed in chat ui state`() = runTest {
    viewModel.sendMessage("胸痛怎么办")
    val state = viewModel.uiState.value

    assertTrue(state.latestWarnings.isNotEmpty())
    assertTrue(state.messages.last().warnings.isNotEmpty())
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang && ./gradlew test --tests '*ChatViewModelTest'`
Expected: FAIL because the UI state does not track warnings.

- [ ] **Step 3: Write the failing upload-viewmodel test**

```kotlin
@Test
fun `upload state exposes processing phase and retry flag`() = runTest {
    viewModel.refreshReportStatus(10002)
    val state = viewModel.uiState.value

    assertEquals("INDEXING", state.reportPhase)
    assertEquals(true, state.canRetry)
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `cd /Users/a1234/code/doctor-wang && ./gradlew test --tests '*UploadViewModelTest'`
Expected: FAIL because processing fields are not in the UI state.

- [ ] **Step 5: Implement the UI state additions**

```kotlin
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isSending: Boolean = false,
    val latestWarnings: List<String> = emptyList(),
)

data class UploadUiState(
    val isUploading: Boolean = false,
    val reportPhase: String? = null,
    val canRetry: Boolean = false,
    val failureCategory: String? = null,
)
```

- [ ] **Step 6: Render warnings and processing summaries in Compose**

```kotlin
if (uiState.latestWarnings.isNotEmpty()) {
    WarningCard(messages = uiState.latestWarnings)
}

Text(text = uiState.reportPhase ?: "等待处理")
if (uiState.canRetry) {
    Text(text = "处理失败后可重试")
}
```

- [ ] **Step 7: Run the targeted tests**

Run: `cd /Users/a1234/code/doctor-wang && ./gradlew test --tests '*ChatViewModelTest' --tests '*UploadViewModelTest'`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/ChatViewModel.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/UploadViewModel.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/ReportDetailViewModel.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/chat/ChatScreen.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/upload/UploadScreen.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/ui/screen/report/ReportDetailScreen.kt \
  app/src/test/java/com/ipuuuuu/doctorwang/viewmodel/ChatViewModelTest.kt \
  app/src/test/java/com/ipuuuuu/doctorwang/viewmodel/UploadViewModelTest.kt

git commit -m "feat: surface warnings and report processing states"
```

### Task 6: Add Cross-Service QA Gates and Documentation Updates

**Files:**
- Modify: `docs/technical-decisions.md`
- Modify: `README.md`
- Modify: `doctor-wang-mock-server/README.md`
- Modify: `ai-service/README.md`
- Modify: `docs/roadmap.md`
- Test: `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/integration/PostgresIntegrationMatrixTest.kt`
- Test: `ai-service/tests/test_api_contract.py`
- Test: `app/src/test/java/com/ipuuuuu/doctorwang/data/repository/RemoteDoctorWangRepositoryTest.kt`

**Interfaces:**
- Consumes: all public/internal contracts from Tasks 1–5.
- Produces:
  - Documented backend response contract
  - Documented AI-service route contract
  - Documented Android expectations for warnings/status states
  - A repeatable verification command list for QA

- [ ] **Step 1: Write the failing backend integration assertion for traceable report status**

```kotlin
@Test
fun `report processing flow persists traceable status fields`() {
    val response = reportApi.fetchStatus(reportId = 10002)
    assertNotNull(response.phase)
    assertNotNull(response.traceId)
}
```

- [ ] **Step 2: Run test to verify it fails or proves the gap**

Run: `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && ./gradlew test --tests '*PostgresIntegrationMatrixTest'`
Expected: FAIL or expose missing assertions that need to be added before the fix.

- [ ] **Step 3: Update the documentation with exact contracts and QA commands**

```markdown
## Contract verification

Backend:
- `cd doctor-wang-mock-server && ./gradlew test --tests '*ReportV1ControllerTest' --tests '*ChatControllerTest' --tests '*AiServiceClientContractTest' --tests '*PostgresIntegrationMatrixTest'`

AI service:
- `cd ai-service && pytest tests/test_api_contract.py tests/test_safety.py tests/test_pipeline.py -v`

Android:
- `./gradlew test --tests '*RemoteDoctorWangRepositoryTest' --tests '*ChatViewModelTest' --tests '*UploadViewModelTest'`
```

- [ ] **Step 4: Add the technical decision entry**

```markdown
## 2026-08-08 — Freeze Doctor-Wang cross-service contracts before feature expansion

Decision: Stabilize the backend public API envelope, the backend↔AI-service internal DTOs, and the Android-facing warning/report-status models before expanding new AI features.

Why: This reduces integration churn across Android, Spring Boot, and the AI service while preserving medical-safety and traceability requirements.

How to apply: New chat/report features must extend the stabilized contracts and keep warnings, citations, status fields, and trace IDs intact.
```

- [ ] **Step 5: Run the full verification set**

Run: `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && ./gradlew test --tests '*ReportV1ControllerTest' --tests '*ChatControllerTest' --tests '*AiServiceClientContractTest' --tests '*ReportJobStateMachineTest' --tests '*PostgresIntegrationMatrixTest' && cd /Users/a1234/code/doctor-wang/ai-service && pytest tests/test_api_contract.py tests/test_safety.py tests/test_pipeline.py -v && cd /Users/a1234/code/doctor-wang && ./gradlew test --tests '*RemoteDoctorWangRepositoryTest' --tests '*ChatViewModelTest' --tests '*UploadViewModelTest'`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add docs/technical-decisions.md docs/roadmap.md README.md \
  doctor-wang-mock-server/README.md ai-service/README.md \
  doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/integration/PostgresIntegrationMatrixTest.kt \
  ai-service/tests/test_api_contract.py ai-service/tests/test_safety.py ai-service/tests/test_pipeline.py \
  app/src/test/java/com/ipuuuuu/doctorwang/data/repository/RemoteDoctorWangRepositoryTest.kt

git commit -m "docs: add contract verification and qa gates"
```

## Self-Review

### Spec coverage

- 总体架构按 Backend → AI Service → Android → QA 顺序展开：covered by Tasks 1–6.
- Backend 基础能力（API、错误模型、状态机、契约）：covered by Tasks 1–2.
- AI Service / RAG / Safety 边界：covered by Task 3.
- Android 对接与 UI 状态：covered by Tasks 4–5.
- QA 门禁、文档、验证命令：covered by Task 6.
- 医疗/安全/隐私/traceability constraints: enforced in Global Constraints and tested in Tasks 1, 3, and 6.

### Placeholder scan

- No `TBD`, `TODO`, or “implement later” markers remain.
- Each code step includes concrete code blocks.
- Each test step includes a concrete command and expected result.

### Type consistency

- `ReportStatusResponse`, `ChatReplyResponse`, `AiIngestionSubmitRequest`, `AiIngestionStatusResponse`, `MessageCitation`, and `MedicalReport` names are consistent across tasks.
- Android tasks only consume models defined in earlier tasks.
- QA task verifies the exact contracts introduced earlier rather than inventing new ones.
