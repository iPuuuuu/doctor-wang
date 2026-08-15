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
生产化阶段。ANDROID-001、PROD-001、SEC-001、DEPLOY-001a 已 ACCEPTED。本地测试实测：Android 15 + backend 78 + AI 97 = **190 tests PASS**、CI 全绿（含 Testcontainers 集成矩阵）。DEPLOY-001b（证书/监控/HA/ClamAV 编排）方案就绪（ADR-0009）+ 监控脚本已备。Remaining blockers: 生产服务器 120.55.186.220 OOM 卡死待用户重启、Android 真机验证、域名证书。

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

## RAG-001 真实 Provider 验证（2026-08-14，用户提供 DeepSeek + 智谱 + 通义 key）

- Key 仅经环境变量使用，未入库/未落日志/未落测试样本；仓库内已确认零 key 残留。
- **真实全链路已通过（图片/扫描件）**：通义 `qwen-vl-ocr` 识别 → 智谱 `embedding-3`（2048 维）语义检索 → DeepSeek `deepseek-v4-flash` 生成；脱敏图片 ingest COMPLETED → rag/query OK（citation 1，答案基于 OCR 原文，零编造）；OCR 文本不足时正确走 REVIEW_REQUIRED。
- 实现：`DashScopeOcrProvider`（ai-service，89 pytest 全绿）；`deploy/.env.example` 含 `DW_AI_*`（LLM/EMBEDDING/OCR）占位。
- 探测结论：Claude/Gemini 中转站（code.newcli.com）无 embedding 模型，不作为 provider。
- **ECS 生产部署完成（2026-08-14）**：阿里云 120.55.186.220 全栈上线（PostgreSQL+pgvector / backend / AI / Nginx TLS），Flyway V1-V10、真实 AI 全链路（通义 OCR 配置 + 智谱 embedding-3 2048 维 pgvector 落库 + DeepSeek 生成）外部 HTTPS 200 验证通过；自签证书为已知限制（需域名+受信任证书）。CI 全绿（含 Testcontainers）。
- 2026-08-15 用户向重构：删除未绑定的 Fake 仓库与占位 URL；ChatService 关键词 mock 回复改为诚实"不可用"提示；AI 服务缺真实 provider 时醒目告警（不静默降级）；VIRUS_SCAN_ENABLED 默认 true；上传/详情页错误码映射为通俗文案。全部后端能力已暴露到 App（上传/列表/详情/状态/重试/删除/下载原文件/问诊/安心话聊/档案/改密/退出）。
- 2026-08-15 新增：**安心话聊（疑病症/大病焦虑缓解）**——AI service `mode=anxiety` + 专用提示词（结合健康档案+报告、区分担忧与证据、医疗边界、心理科转介），后端 `POST /api/v1/chat/anxiety`，Android 安心话聊页面 + 首页入口；本地全链路实测（DeepSeek+智谱+PG）回复循证且安全；provider/read 超时放宽（60s/90s）。UI 美化：完整 Material3 主题、登录/注册/首页/安心话聊页面打磨。
- 生产服务器（120.55.186.220）：2026-08-14 晚因 1.6GB 无 swap OOM 卡死（SSH 持续超时约 24h），用户 8-15 在阿里云控制台"停止→启动"后恢复。**恢复部署完成（2026-08-15 20:1x）**：2G swap+内核参数+Docker ulimit 加固、后端最小镜像（最新 jar，Flyway V1→V11 生产库自动迁移成功）、ai-service 离线重建（旧镜像作基础层+阿里云 pip 镜像）、全栈 healthy、健康巡检 cron 已装、collation 修复。**端到端实测通过（外部 HTTPS）**：healthz/actuator UP、demo 登录、普通问诊（citation 1）、安心话聊（真实引用"肝脏形态大小正常…未见占位性病变"+结合档案+医疗边界）、APK 下载 200（application/vnd.android.package-archive）。期间修复 1 个真实问题：reportlab 默认字体中文 PDF 被 pypdf 提取为 ■（改 STSong-Light 重生成并重新上传，示例报告 report 10005 已向量化）。
- 2026-08-15 V11：问诊与安心话聊会话隔离（chat_message.conversation 列，问诊 history 与 anxiety history 独立）；Android 多处 UX 修复（报告列表返回刷新、删除确认、下载 MIME、会话过期跳登录）。
- **服务器已恢复并上线最新版（2026-08-15）**：见上条；v2 APK 下载地址 `https://120.55.186.220/downloads/doctor-wang-debug-v2.apk`（新文件名避开浏览器缓存，旧 `doctor-wang-debug.apk` 仍留在 /srv/downloads 未删）。
- 仍待：Android 真机验证（用户安装 v2 APK）；域名+ICP 备案+正式证书（指南 `docs/guide-domain-and-release.md`）；**ClamAV 病毒扫描（已实测并决策搁置：1.6GB 内存装不下全量病毒库，clamd 需 700MB-1GB 内存导致整机换页卡死——用户决策"不影响使用即可"，VIRUS_SCAN_ENABLED=false，镜像+scan profile+镜像加速已就绪，升级 4GB+ 内存后开 true）**；release 签名包。
- 2026-08-14 本地 PG17+pgvector 实测：Flyway V1–V10 应用、Hibernate validate 通过、PgVectorStore 真实 2048 维向量、后端↔AI 全链路（上传→AI 处理→COMPLETED→聊天 citation 1）——期间修复 7 个真实生产 bug（V6 undo 重复、V9 HNSW 2000 维上限、V10 weight 类型、actuator 过滤、worker 事务自调用、实体缺无参构造器、chat namespace 用户 ID 错误；AI_SERVICE_* env 改 APP_AI_SERVICE_*）。
- 证据：`.ai/handoffs/RAG-001/architect-pgvector-fullchain-verification.md`、`architect-real-llm-verification.md`、`architect-real-embeddings.md`、`architect-real-ocr.md`。

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
