---
name: task-board
description: 当前 AI Harness 的任务看板
metadata:
  type: project
---

# Task Board

## Lifecycle Reference

```
BACKLOG → READY → IN_PROGRESS → REVIEW → QA → ACCEPTED → DONE
                                                    ↓
任何状态 ──→ BLOCKED ──→ ESCALATED ──→ Architect 决策
```

Full state machine: `.ai/workflows/task-lifecycle.md`

## Backlog

| ID | Task | Priority | Dependencies | Status |
|---|---|---|---|---|
| ARCH-001 | 建立项目架构 | P2 | — | ACCEPTED ✅ |
| RAG-001 | 完成 RAG 系统 | P1 | BACKEND-001, CHAT-001 | ACCEPTED ✅（真实全链路本地实测：pgvector V9/V10 + 通义 OCR + 智谱 embedding + DeepSeek 生成；7 个生产 bug 已修） |
| AGENT-001 | 完成 Agent 系统 | P2 | RAG-001 | ACCEPTED ✅ (A1-A3 done; 24 tests) |
| ANDROID-001 | 完成 Android 客户端 | P1 | BACKEND-001 | ACCEPTED ⚠️（A1–A8 done；单测 15/15 PASS；assembleDebug PASS；QA 两轮复核后 P1/P2 全部修复；真机验证延期） |

## Next Phase Backlog（生产化，2026-08-14 建立，待 Architect 逐项批准）

| ID | Task | Priority | Dependencies | Status |
|---|---|---|---|---|
| PROD-001 | 后端 Java 21 升级（ADR-0007 + 兼容方案 + 构建验证） | P1 | — | ACCEPTED ✅（toolchain 21 + Dockerfile 21 + 56/56 tests on JDK 21 + bootJar major 65；镜像启动验证延 Docker） |
| DEPLOY-001 | 部署生产化加固 | P1 | PROD-001, SEC-001 | PARTIAL：001a ✅（actuator）；**001c ECS 生产部署 ✅（2026-08-14 上线验证）**；001b（域名证书/监控/HA/ClamAV 启用）待续 |
| SEC-001 | 上传安全生产化（NoopVirusScanner → ClamAV fail-closed、对象生命周期） | P1 | BACKEND-001 | ACCEPTED ✅（ADR-0008；ClamAvVirusScanner fail-closed + 13 新测试；后端 69/69 PASS；真实 ClamAV 冒烟延 Docker；对象生命周期为后续切片） |

> 环境依赖缺口（本会话确认）：Docker/容器运行时与原生 PostgreSQL 均不可用 → PostgreSQL/Testcontainers 集成测试、TLS/Nginx 冒烟、post-revoke 端到端证据仍须在 Docker-capable 环境补跑；无 JDK 21 → PROD-001 构建验证需在具备 JDK 21 环境执行或安装后复跑；NoopVirusScanner 为开发占位，生产扫描未实现。

## Completed Tasks (ACCEPTED/DONE)

### AUTH-001 — JWT 登录垂直切片 ✅

- Owner: Architect
- Backend: JWT issuer/verifier、PBKDF2/BCrypt credential verification、revocable JTI session、Flyway V8
- Android: password input semantics、401 session clearing、no BODY logging
- QA: JWT unit tests、credential hashing tests、migration/integration gate
- Status: ACCEPT (基础设施依赖测试延期)
- Local evidence: root `bash ./gradlew test` PASS with Android unit tests `NO-SOURCE`; exact path-and-method filter tests PASS; `BackendServiceTest` PASS (5 tests) after Mockito matcher repair; latest complete backend `bash ./gradlew --no-daemon test` PASS; `git diff --check` PASS. QA final review PASS. Quality final review accepts method-lockdown but marks combined logout/revoke REWORK for missing real repository + subsequent-request 401 and cross-user/JTI evidence; PostgreSQL/Testcontainers is BLOCKED without Docker.
- Completed sub-slice: TTL is constrained to 1–86,400 seconds; JOSE `alg=HS256` / `typ=JWT` are required; `AuthProperties` fail-fast validates secret/issuer/audience/TTL with explicit boundary tests; direct verifier tests cover expiry/issuer/audience/missing claims/malformed tokens; focused filter tests now prove 401/503 behavior for missing token, malformed token, verifier expiry, revoked persisted session, expired persisted session, and valid session pass-through; `PostgresIntegrationMatrixTest` asserts Flyway V1–V8 and V8 core schema fields/unique JTI constraint.
- Gate result: 所有本地可运行测试 PASS；`git diff --check` PASS。
- Deferred (需 Docker/设备/CI 环境，不在本地阻塞):
  1. PostgreSQL 集成测试 (Testcontainers) — Docker 不可用
  2. TLS/Nginx/HTTPS smoke — Docker Compose 不可用
  3. 真机 Android 测试 — 无设备
  4. 真实仓库 post-revoke 端到端证据 — 依赖 Testcontainers
- Next: Docker-capable CI 补跑延期测试；backlog 任务可启动。

### BACKEND-001 — 报告 CRUD、用户管理、文件上传等基础能力 ✅

- Status: ACCEPTED → DONE
- Evidence: `bash ./gradlew --no-daemon test` PASS (57 tests); 6 new endpoints; no Flyway migration needed.

### P0-FIXES — 安全修复 (FIX-A1/A2/A3) ✅

- Status: ACCEPTED → DONE
- FIX-A1: GlobalExceptionHandler structural fix (handler nesting)
- FIX-A2: changePassword session revoke (ADR-0003 compliance)
- FIX-A3: ACCOUNT_EXISTS → 409 Conflict

### REF-B — 架构清理 (REF-B1/B2/B3/B4) ✅

- Status: ACCEPTED → DONE
- REF-B1: BackendService 291→149 行, separate ChatService
- REF-B2: Android getReport migrated to /api/v1/reports/{id}
- REF-B3: Legacy ReportController deleted
- REF-B4: AppUserEntity mutable fields var-ified

### CHAT-D1 — Chat AI Service RAG 集成 ✅

- Status: ACCEPTED → DONE
- ChatService with AI Service RAG + feature flag + mock fallback

### RAG-001 — RAG 系统完成 ⚠️

- Status: ACCEPTED (partial — real embeddings/OCR/LLM need API keys)
- RAG-R1: 43 comprehensive tests (embeddings, chunking, vector store, safety, E2E)
- RAG-R2: 5 golden scenarios with 10 query expectations
- RAG-R3: PgVectorStore factory wiring (feature-flagged, falls back to InMemory)
- RAG-R4: Safety gate expanded (20 HIGH + 13 MEDIUM risk terms, 3-tier)
- Total: 62 AI tests + 57 backend tests = 119 tests
- Deferred: real embeddings, OCR, LLM generation (no API keys); PgVector integration (no Docker)

## Operational Task

### BOT-001 — Codex Managed Execution ✅

- Status: VERIFIED
- Evidence: Feishu long connection established, `codex exec` smoke test passed.

## Integration Change

### REPORT-AI-001 — Android v1 Upload + AI Service Integration ⚠️

- Status: ACCEPTED ⚠️ (test failures resolved; Docker-dependent tests deferred)
