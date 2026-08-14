---
name: project-state
description: 当前项目阶段与执行状态
metadata:
  type: project
---

# Project State

Project:
Doctor-Wang

Harness Version:
v2 (2026-08-12) — State directory, task lifecycle, agent receipts, escalation workflow

Current Phase:
生产化阶段。ANDROID-001、PROD-001、SEC-001、DEPLOY-001a 已 ACCEPTED。本地测试实测：Android 15 + backend 70 + AI 86 = **171 tests PASS**。DEPLOY-001b（证书/监控/HA/ClamAV 编排/CI）方案就绪（ADR-0009），实施依赖 Docker/生产环境。Remaining blockers: Docker（PostgreSQL/Testcontainers、TLS 冒烟、21-jre 镜像启动、真实 ClamAV 冒烟、compose healthcheck 验证）、Android 真机/模拟器、API keys。

Status:
AUTH-001 ACCEPTED (deferred tests). BACKEND-001 ACCEPTED — 报告 CRUD、用户管理、文件上传等基础能力已实现。 The MockMvc login regression and method-agnostic public-route bypass are closed by exact path-and-method matching. The approved TLS, pgcrypto BCrypt migration, server-side logout, Android server revoke, and release HTTPS URL guard are implemented and locally compiled. All locally-runnable tests PASS. PROD-001 ACCEPTED — Java 21 LTS（ADR-0007）：toolchain 21 + Dockerfile 21-jdk/21-jre，JDK 21 上 70/70 tests PASS、字节码 major 65。 SEC-001 ACCEPTED — 上传病毒扫描 fail-closed（ADR-0008）：ClamAvVirusScanner + 条件装配 + 13 新测试；真实 ClamAV 冒烟延 Docker。 DEPLOY-001a ACCEPTED — actuator /actuator/health 端点（ADR-0009）：白名单暴露 + MockMvc 实测 UP + compose healthcheck；DEPLOY-001b 方案就绪待环境。

## Current Task

- Owner: Architect
- Scope: 生产化阶段 — PROD-001、SEC-001、DEPLOY-001a 已完成；DEPLOY-001b 方案就绪（ADR-0009），实施依赖 Docker/生产环境
- Status: DEPLOY-001a ACCEPTED ✅；DEPLOY-001b BACKLOG（环境依赖）
- Evidence（本会话全部实测）：
  - PROD-001：JDK 21（工作区自带）+ toolchain 21 → `clean test bootJar` 56/56 PASS、字节码 major 65
  - SEC-001：ClamAvVirusScannerTest 6 + VirusScanConfigTest 3 + ReportUploadScanBehaviorTest 4 = 13 新测试，后端 69/69
  - DEPLOY-001a：ActuatorHealthTest（最小上下文）`/actuator/health` → 200 UP，后端 70/70
  - `git diff --check` PASS（全仓）
- 环境依赖缺口（本会话确认）：
  1. Docker/容器运行时与原生 PostgreSQL 均不可用 → PostgreSQL/Testcontainers、TLS/Nginx 冒烟、post-revoke 端到端、21-jre 镜像启动、真实 ClamAV 冒烟、compose healthcheck 均须 Docker-capable 环境补跑
  2. `NoopVirusScanner`（enabled=false 默认）为开发占位，生产须 VIRUS_SCAN_ENABLED=true
  3. 证书自动供给/监控告警/HA/CI（DEPLOY-001b）需生产或 Docker 环境
  4. Android 真机/模拟器、AI provider API keys 未提供
- Next step: 提供 Docker/生产环境实施 DEPLOY-001b 并补跑全部延期验证；或提供 API keys/真机；或转入下一阶段规划（如对象生命周期、AI 生产化、多租户等）。

## RAG-001 真实 Provider 验证（2026-08-14，用户提供 DeepSeek + 智谱 key）

- Key 仅经环境变量使用，未入库/未落日志/未落测试样本；仓库内已确认零 key 残留。
- **真实检索 E2E 已通过**：智谱 `embedding-3`（2048 维）+ DeepSeek `deepseek-v4-flash` 生成；脱敏 PDF ingest COMPLETED → rag/query OK / risk LOW / citation confidence 1.0 / needsReview false；来源不足时模型安全拒答；namespace 归属授权校验在真实链路生效。
- 探测结论：Claude/Gemini 中转站（code.newcli.com）均无 embedding 模型，不作为 embedding 来源。
- 仍为 mock：OCR（图片型报告解析，需 OCR 端点）。
- 生产启用方式：`deploy/.env.example` 已含 `DW_AI_LLM_*` / `DW_AI_EMBEDDING_*` / `VIRUS_SCAN_*` 占位；密钥走 secrets 管理。
- 证据：`.ai/handoffs/RAG-001/architect-real-embeddings.md`、`architect-real-llm-verification.md`；86 AI 测试不受影响。

## Operational Runner

- Task: BOT-001 — switch local managed bot execution from Claude CLI to Codex CLI
- Status: VERIFIED
- Evidence: `.ai/handoffs/BOT-001/architect.md`; bot AST and `git diff --check` PASS; `codex --version` PASS; authorized read-only `codex exec --ephemeral --json ...` returned `thread.started`, `turn.completed`, and `READY`.
- Evidence: bot process is running in session `7326`; Feishu long connection established at 2026-08-09 23:03:03; background polling is active.
- Note: `codex doctor` still reports intermittent Provider health-probe reachability, but an authorized smoke call succeeded; this is not currently an API-key blocker.

## Concurrent Integration Change

- Task: REPORT-AI-001 — Android v1 multipart upload and AI Service development integration
- Status: REWORK / NOT PRODUCTION READY
- Evidence: Android root build PASS with test tasks `NO-SOURCE`; AI `pytest` 9 passed; backend forced Kotlin compilation PASS; `git diff --check` PASS.
- Blockers: Docker is unavailable, so Compose, TLS, PostgreSQL/Testcontainers, and device-to-backend-to-AI smoke checks are NOT RUN. Production service identity, object storage, scanning, PgVector and medical evaluation gates remain BLOCKED.

## Backend Foundational Capabilities

- Task: BACKEND-001 — 报告 CRUD、用户管理、文件上传等基础能力
- Status: ACCEPT
- Scope: Report list (GET /api/v1/reports), file download (GET /api/v1/reports/{id}/file), user registration (POST /api/register), profile update (PUT /api/profile), password change (POST /api/password/change), chat response alignment (warnings/citations/traceId)
- Evidence: `bash ./gradlew --no-daemon test` PASS (57 tests); `bash ./gradlew --no-daemon compileKotlin --rerun-tasks` PASS; `git diff --check` PASS. Backend tests cover register, updateProfile, changePassword, chat response shape, register route bypass, profile/password contract auth checks.
- Modified files:
  - Backend: `AuthController.kt` (+register, +password/change), `ProfileController.kt` (+PUT /profile), `ReportV1Controller.kt` (+list, +file download), `BackendService.kt` (+register, +updateProfile, +changePassword, +chat alignment), `ReportProcessingService.kt` (+listByUser, +getFile), `FileStorageService.kt` (+retrieve), `LocalFileStorageService.kt` (+retrieve impl), `MedicalReportRepository.kt` (+Pageable variant), `AuthTokenFilter.kt` (+POST /api/register public), `ApiModels.kt` (+DTOs, +updated ChatResponse), `ReportApiModels.kt` (+list/detail DTOs)
  - Tests: `BackendServiceTest.kt` (+7 tests), `AuthTokenFilterTest.kt` (+register bypass), `AuthHttpContractTest.kt` (+5 contract tests)
  - Android: `DoctorWangApi.kt` (+register, +updateProfile, +changePassword, +listReports, +downloadReportFile, +DTOs)
- No Flyway migration needed (schema already supports all operations).
- Known limitations: Chat still uses mock keyword-based replies (AI Service integration deferred to RAG/Agent phase); Account deletion not yet implemented; No pagination enforcement on empty report list; File download not yet tested with large binaries.
