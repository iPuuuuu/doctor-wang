# Doctor-Wang Architecture Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rework Doctor-Wang into a contract-driven Android + Spring Boot + AI Service architecture with clear domain boundaries, safety controls, and long-term evolvability.

**Architecture:** Keep Android as the presentation client, Spring Boot as the system of record and orchestration layer, and FastAPI/LangGraph as the AI capability layer. Align the design with DeepTutor-style principles: explicit runtime/state, modular capabilities, configuration outside code, and clear separation between session, profile, report, file, and memory domains.

**Tech Stack:** Kotlin, Jetpack Compose, ViewModel, Repository, Hilt, Spring Boot 3, Kotlin/JVM 17, PostgreSQL, Flyway, FastAPI, LangGraph, Python 3.11+, Docker Compose.

## Global Constraints

- Android 不得直连模型供应商；AI 调用必须通过受控服务端。
- 数据库结构变更必须新增 Flyway migration，禁止直接改已有 migration。
- 不把 mock 能力伪装成 production AI 能力。
- 不把密码、token、健康数据、报告内容写入日志或提交到仓库。
- 医疗输出不得输出确定性诊断；高风险症状必须进行就医分流和风险提示。
- Spring Boot 当前事实基线为 Kotlin/JVM 17 + PostgreSQL；目标 Java 21 迁移必须单独提出兼容性方案，不能默默迁移。
- AI Service 当前未作为独立服务稳定成型；任何拆分必须保持后端权限边界为唯一权威来源。
- 任何对外 API 变更必须保留兼容窗口，避免 Android 端一次性断裂。

---

### Task 1: Lock the target architecture boundaries

**Files:**
- Modify: `docs/architecture.md`
- Modify: `docs/design/rag-upload.md`
- Create: `docs/design/doctor-wang-target-architecture.md`

**Interfaces:**
- Consumes: current project context from `.ai/context.md`, `.ai/roles/*.md`, and existing design docs.
- Produces: a single target architecture spec with explicit boundaries for Android, backend, AI Service, session, profile, report, file, memory, observability, and safety.

- [ ] **Step 1: Write the boundary matrix in the design doc**

```markdown
| Domain | System of record | Read model | Write path | Notes |
|---|---|---|---|---|
| session | Spring Boot | Android cache + AI context | Backend | short-lived conversation state |
| profile | Spring Boot | Android cache | Backend | stable user facts |
| report | Spring Boot | Android cache | Backend → AI Service | async processing |
| file | Spring Boot + object storage | Android cache | Backend | raw and derived artifacts |
| memory | Spring Boot + AI Service | Android cache | Backend → AI Service | low-risk reusable context |
```

- [ ] **Step 2: Replace ambiguous “AI platform” wording with explicit service responsibilities**

```markdown
- Android: UI, interaction state, offline cache, upload/report entry points
- Spring Boot: auth, ownership, orchestration, audit, API versioning, job state
- AI Service: parsing, RAG, agent workflows, safety review, evaluation
```

- [ ] **Step 3: Add a concise anti-pattern section**

```markdown
## Anti-Patterns To Avoid
- Android direct model calls
- report text stored only inside chat messages
- agent deciding authorization
- hidden global state for memory or prompt configuration
```

- [ ] **Step 4: Verify the doc reads as the single source of truth for the target state**

Run: review `docs/design/doctor-wang-target-architecture.md` for duplicated claims between sections
Expected: one coherent architecture narrative, no contradictions between domain table and module split

- [ ] **Step 5: Commit the document set**

```bash
git add docs/architecture.md docs/design/rag-upload.md docs/design/doctor-wang-target-architecture.md
git commit -m "docs: define doctor-wang target architecture"
```

### Task 2: Make the Android client boundaries explicit

**Files:**
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/ui/navigation/AppNavGraph.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/ui/navigation/Screen.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/ChatViewModel.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/ReportDetailViewModel.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/UploadViewModel.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/data/repository/DoctorWangRepository.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/data/model/MedicalReport.kt`
- Modify: `app/src/main/java/com/ipuuuuu/doctorwang/data/model/Message.kt`

**Interfaces:**
- Consumes: target API contract from Task 1.
- Produces: Android-side state boundaries for chat, report, upload, and profile without shared “god” state.

- [ ] **Step 1: Add explicit UI state types for each domain**

```kotlin
sealed interface ReportProcessingUiState {
    data object Idle : ReportProcessingUiState
    data object Uploading : ReportProcessingUiState
    data object Processing : ReportProcessingUiState
    data object Completed : ReportProcessingUiState
    data object Failed : ReportProcessingUiState
    data object ReviewRequired : ReportProcessingUiState
}
```

- [ ] **Step 2: Split repository responsibilities by domain**

```kotlin
interface ChatRepository
interface ReportRepository
interface ProfileRepository
interface SessionRepository
```

- [ ] **Step 3: Remove cross-domain coupling from the shared repository API**

```kotlin
// Avoid one repository that returns chat, report, and profile data together.
```

- [ ] **Step 4: Verify navigation and ViewModel ownership**

Run: inspect each screen/viewmodel pair and confirm it only depends on the repository for its domain
Expected: chat screen cannot mutate report state; report screen cannot depend on chat-specific message models

- [ ] **Step 5: Add or update tests around state mapping**

```kotlin
@Test
fun `report failure maps to failed ui state`() { /* ... */ }
```

- [ ] **Step 6: Commit the Android boundary updates**

```bash
git add app/src/main/java/com/ipuuuuu/doctorwang/ui/navigation/AppNavGraph.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/ui/navigation/Screen.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/viewmodel/*.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/data/repository/*.kt \
  app/src/main/java/com/ipuuuuu/doctorwang/data/model/*.kt
git commit -m "refactor(android): separate domain boundaries"
```

### Task 3: Refactor backend into explicit domain and orchestration layers

**Files:**
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/BackendService.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportProcessingService.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportJobStateMachine.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/AiServiceClient.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/FileStorageService.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/ReportNamespaceAuthorizer.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/controller/ReportV1Controller.kt`
- Modify: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/model/ReportContract.kt`

**Interfaces:**
- Consumes: Android-facing contract and target architecture doc.
- Produces: backend service boundaries for auth, report orchestration, file handling, and AI client calls.

- [ ] **Step 1: Separate orchestration from state mutation**

```kotlin
class ReportOrchestrationService(
    private val reportCommandService: ReportCommandService,
    private val reportQueryService: ReportQueryService,
    private val aiServiceClient: AiServiceClient,
)
```

- [ ] **Step 2: Move authorization checks to a dedicated boundary service**

```kotlin
class ReportNamespaceAuthorizer {
    fun assertCanRead(userId: Long, reportId: Long)
}
```

- [ ] **Step 3: Keep the AI client as a narrow internal adapter**

```kotlin
interface AiServiceClient {
    fun submitReportJob(request: ReportJobRequest): ReportJobResponse
    fun queryRag(request: RagQueryRequest): RagQueryResponse
}
```

- [ ] **Step 4: Verify controller thinness**

Run: review controller methods and confirm they only translate HTTP ↔ use-case calls
Expected: no business logic, no direct repository access, no inline state machine logic

- [ ] **Step 5: Add or update backend tests for auth, state transitions, and error mapping**

```kotlin
@Test
fun `unauthorized report access is rejected before ai request`() { /* ... */ }
```

- [ ] **Step 6: Commit the backend boundary refactor**

```bash
git add doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/**/*.kt
git commit -m "refactor(backend): clarify orchestration boundaries"
```

### Task 4: Define the AI service runtime and agent boundaries

**Files:**
- Modify: `ai-service/app/main.py`
- Modify: `ai-service/app/graph.py`
- Modify: `ai-service/app/schemas.py`
- Modify: `ai-service/app/config.py`
- Modify: `ai-service/app/safety.py`
- Modify: `ai-service/app/rag.py`
- Modify: `ai-service/app/vectorstore.py`
- Modify: `ai-service/app/providers.py`
- Modify: `ai-service/app/parsing.py`
- Modify: `ai-service/app/jobs.py`
- Modify: `ai-service/tests/test_pipeline.py`

**Interfaces:**
- Consumes: backend internal request contract and target architecture doc.
- Produces: explicit AI runtime boundaries for parsing, retrieval, generation, safety, and evaluation.

- [ ] **Step 1: Split the graph into named stages**

```python
REPORT_PIPELINE_STAGES = [
    "ingest",
    "parse",
    "chunk",
    "embed",
    "retrieve",
    "generate",
    "safety_review",
]
```

- [ ] **Step 2: Define schema boundaries for internal requests and outputs**

```python
class ReportJobRequest(BaseModel):
    report_id: str
    file_uri: str
    user_id: str
    trace_id: str
```

- [ ] **Step 3: Make safety gating an explicit post-processing step**

```python
class SafetyDecision(BaseModel):
    allow: bool
    requires_review: bool
    reason: str
```

- [ ] **Step 4: Verify the AI service never decides authorization**

Run: search the AI service for `user_id` usage and confirm it is only used for scoping/trace/correlation, not permission checks
Expected: backend remains the only authority for access decisions

- [ ] **Step 5: Add tests for stage ordering, safety gating, and schema validation**

```python
def test_pipeline_runs_safety_review_after_generation():
    # assert order and output contract
    ...
```

- [ ] **Step 6: Commit the AI runtime boundaries**

```bash
git add ai-service/app/*.py ai-service/tests/test_pipeline.py
git commit -m "refactor(ai): formalize runtime and safety boundaries"
```

### Task 5: Document observability, configuration, and evolution rules

**Files:**
- Modify: `docs/architecture.md`
- Modify: `README.md`
- Create: `docs/design/observability-config-evolution.md`

**Interfaces:**
- Consumes: the target architecture from Task 1 and the service boundary work from Tasks 2–4.
- Produces: a short operating guide for traceability, config ownership, and safe evolution.

- [ ] **Step 1: Document the required correlation identifiers**

```markdown
- traceId
- requestId
- jobId
- reportId
- userId
- modelVersion
- promptVersion
```

- [ ] **Step 2: Document config ownership by layer**

```markdown
- Android: feature flags, UI preferences
- Spring Boot: API endpoints, auth/session, job policy, storage config
- AI Service: model routing, prompt registry, retrieval params, safety thresholds
```

- [ ] **Step 3: Define the evolution rules for future splits**

```markdown
- split only when a boundary is stable and independently testable
- keep ownership in backend until AI behavior is safe and observable
- avoid introducing extra storage or queue layers until load justifies it
```

- [ ] **Step 4: Verify docs are consistent with the architecture decision**

Run: compare `docs/architecture.md`, the new target architecture doc, and `README.md`
Expected: no conflict between stated goals, no stale references to old architecture assumptions

- [ ] **Step 5: Commit the operational guidance**

```bash
git add docs/architecture.md README.md docs/design/observability-config-evolution.md
git commit -m "docs: capture observability and evolution rules"
```

## Validation Checklist

- Android state ownership is split by domain and no longer relies on a shared catch-all repository.
- Backend exposes explicit orchestration, authorization, and storage boundaries.
- AI Service has named pipeline stages, explicit request schemas, and post-generation safety review.
- Report, file, session, profile, and memory are clearly separated in the target design.
- Configuration and observability rules are documented and consistent across docs.
- No task assumes Android can call models directly or that AI can decide authorization.
