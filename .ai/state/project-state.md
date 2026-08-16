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
- 2026-08-15 收尾：**App 全接口线上实测通过**（16 项：login/register/logout/profile PUT+GET/health profile/chat history/anxiety history/report 列表+详情+状态+下载+删除+retry 错误处理/password change 会话撤销/改密后旧 token 失效/登出后 401）；修复健康巡检误报（自签证书 SSL 校验 + IPv6 localhost → `curl -4 -k https://127.0.0.1`）；ClamAV 按用户决策搁置（1.6GB 内存装不下全量病毒库，VIRUS_SCAN_ENABLED=false）。
- 2026-08-15 晚：**报告解读功能（REPORT-INTERPRETATION-001）上线**——修复报告详情页摘要/发现/建议恒为占位的问题：向量化完成后一次循证 RAG 查询生成解读（【摘要】【重点发现】【建议】结构化解析 + AiSafetyGate 校验 + fail-closed 诚实兜底），worker 回写 summary/findings/suggestions（MedicalReportEntity.summary 改 var），App 详情页空板块不再渲染；新增 12 测试（后端 90/90 全绿），生产实测：上传 10008 → COMPLETED → 详情含真实摘要+2 发现+1 建议。期间修 1 个部署失误：docker build 上下文未更新（需先 cp 新 jar 到 deploy/backend-app.jar 再构建）。
- 2026-08-15 晚：**图片（拍照）报告 OCR 全链路线上验证通过**——qwen-vl-ocr 高清图识别 169 字符全文正确 → 向量化 → 解读（真实摘要+2 发现+1 建议）→ 安心话聊引用 OCR 原文（citation 1）。
- 2026-08-15 晚：**上传体验优化上线**——App 上传页新增"检查类型/检查日期"选填输入（仓库/ViewModel/界面联动），后端填类型时标题自动为"XX报告"；修复**处理中删除报告死锁**（delete 先删 job 行，与 worker 锁顺序一致，曾触发 PostgreSQL deadlock 500）；线上实测标题"腹部超声报告"、处理中删除 204。demo 账号 2 份报告（10014 腹部超声报告 PDF、10015 胸部X线报告 PNG/OCR）均 COMPLETED 带真实解读。
- 2026-08-15 晚：**验收清单**（docs/user-acceptance-checklist.md，六板块手机实测步骤）发布并逐项线上核实（含模糊照片→REVIEW_REQUIRED 诚实兜底路径 10016 实测）。
- 2026-08-15 晚：**下载扩展名修复**——友好标题（无扩展名）导致图片报告下载存成 .pdf；改为从响应 MIME 推导扩展名（resolveDownloadExtension 纯函数 + 4 测试，App 19/19）；**自动备份上线**（backup-db.sh：pg_dump + 报告文件 tar，cron 每日 03:10，保留 7 天，实测 18 表 + 原文件可恢复）；**冒烟脚本 smoke-test.sh 10/10 通过**；路线图更新。
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

## 自主改进循环 Round 1（2026-08-16，用户外出期间）

- **App 红线修复**：OkHttp 默认 readTimeout 仅 10s，而 agentic RAG 需 60-90s → 用户聊天必然超时失败；已改为 connect 15s / read 120s / write 60s。
- **agentic 延迟优化**：校验提示词轻量化（只输出 unsupported_claims）+ max_tokens=200 → 生产延迟 66s → 普通问诊 4.4s / 安心话聊 8.5s。
- **数据维护服务**（DataMaintenanceService，每日）：清理过期 session、30 天前撤销 session、过期幂等键、30 天前终态 job（此前这些表无清理，无限增长）。
- **ai-service job 元数据清理**：ingest 时顺带删 30 天前 job（sqlite 防增长）。
- **AI 交互审计上线**（安全合规）：AiAuditEvent 表（V3 建表后一直空置）→ AiAuditService.record 接入问诊/安心话聊，生产实测 AI_CHAT 事件带 traceId/model/风险元数据。
- **评估 v2**：优化后重跑 single vs agentic，忠实度 agentic +0.035（judge 方差分析已记录）。
- 测试：后端 94（+4）、AI 124（+7）；全部推送，工作树干净；冒烟 10/10。

## 自主改进循环 Round 2（2026-08-16）

- **登录暴力破解防护**：账号级失败锁定（15 分钟窗口 5 次失败 → 锁定 15 分钟，成功清零），LOGIN_LOCKED → 429；生产实测 5 次失败后第 6 次被锁、正确密码也被拒。
- **注册 IP 级限流**：IpThrottleService（1 小时 10 次/IP，nginx X-Real-IP），REGISTER_THROTTLED → 429；生产实测第 11 次被拒。
- App 端错误透出链路确认：429/LOGIN_LOCKED 的友好消息会正常展示给用户。
- 清理 23 个测试账号（DB 干净，4 用户）。
- 测试：后端 101（+7）；全部推送。

## 自主改进循环 Round 3（2026-08-16）

- **账号注销（隐私合规）**：DELETE /api/account（需登录）→ 删报告磁盘文件 + 尽力清理向量索引 + 用户行级联删除；App 设置页新增"注销账号"（红色、二次确认、错误提示）。生产实测：注册→注销 204→再登录 401。App 编译通过，APK 已更新。
- **README 测试数字刷新**（后端 101 / AI 124 / Android 19）。
- 教训记录：后端代码变更后必须先 bootJar 再部署（本轮踩了两次旧 jar 坑，已修复）。

## 自主改进循环 Round 4（2026-08-16）

- **上传幂等验证**：同一文件同一 Idempotency-Key 双传 → 同一 reportId（生产实测 dedup 生效，App 双击不会产生重复报告）。
- **ai-service DB 连接自动重连**：PgVectorStore 单例连接断连后自动重建（此前 PG 重启会让所有 AI 查询静默降级直到容器重启）；3 新测试（AI 127/127）。
- **评测集扩到 52 题**（新增数值/边界/拒绝类 15 题）+ 数据集结构校验测试；52 题双引擎评估后台运行中。
- RUNBOOK 补"后端变更须先 bootJar"警示（防旧 jar 部署复发）。

## 自主改进循环 Round 4-5（2026-08-16）

- **52 题评估完成**：评测集扩到 52（+15 数值/边界/拒绝），agentic 忠实度 0.990 > single 0.984（更大样本更稳定）；decline 指标 judge 方差大（四轮 0.625~1.0）已记录；引用有效 100%。
- **ai-service DB 自动重连**：PG 重启后不再静默降级（部署 + 3 测试）。
- **健康巡检资源水位告警**：可用内存<120MB / 磁盘>85% / swap>90% → WARN 记录（OOM 前兆预警）。
- **App 聊天自动滚动到底部**（问诊+安心话聊），APK 已更新。
- **下载文件名 RFC 5987 编码**（浏览器中文名正确），生产验证。
- **上传幂等验证**：同文件同 Key 双传 → 同 reportId。
- API 文档补 DELETE /api/account 与 429。

## 自主改进循环 Round 6（2026-08-16）

- **多轮对话上下文**：RagRequest/AiRagRequest 新增 conversationHistory；ChatService 传最近 6 条消息；rag.py 并入生成上下文。生产验证：追问"那转氨酶和胆红素具体多少？"（省略主语）正确衔接上文给出具体数值。后端 103 / AI 128 全绿。
- **报告详情状态轮询 10s→30s**（覆盖 OCR 图片报告 5-30s 处理），APK 已更新。

## 自主改进循环 Round 7（2026-08-16）

- **注销契约测试**：DELETE /api/account 401（无 token）+ 204（有效会话）→ 后端 105/105。
- **ai-service healthz 增强**：返回 providers（llm/embedding/ocr 配置状态）+ vectorStore 后端（运维可观测，零网络开销）；生产验证 3 provider 全配。
- 复核 App 注册/资料编辑校验与后端边界一致。

## 自主改进循环 Round 8（2026-08-16）

- **并发稳定性验证**：5 并发安心话聊全部 200（7-13s，单 worker 顺序处理，无错误无崩溃）；此前一次"400"系测试脚本 shell 转义把中文变 GBK 所致，非服务问题（已用文件 payload 复测确认）。
- **导航/首页复核**：上传成功→详情跳转正确；BMI 计算有 height=0 边界（注册/更新有 30-250 校验，风险低）。

## 自主改进循环 Round 9（2026-08-16）

- **深色模式适配**：Material3 darkColorScheme（同色相暗色板）+ 安心话聊/登录/注册的 SoftTeal 背景改 theme-aware（isSystemInDarkTheme）；APK 已更新。
- 后端日志审查：3h 内仅 5 条 GBK 解析警告（测试脚本所致），无其他 WARN/ERROR；AI 服务 0 异常。

## 自主改进循环 Round 10（2026-08-16）

- **报告解读专项评估**（新）：解读管线 → LLM-judge（摘要忠实/发现可溯源/建议安全）。过程中发现并修复两个真实问题：① LLM 格式遵从波动（偶发不输出三段式）→ 后端 interpret() 加"空发现/建议时重试一次"（已上线）；② judge 校准（截断/标准过严导致误判，人工复核确认为误判）。结论：解读内容事实质量高（人工核对通过），建议安全率 1.0，小样本 judge 分数如实标注局限。
- 附带发现：**ai-service 存在 site-packages 旧代码副本**（镜像构建时的 pip install），运行中的 uvicorn 实际使用 /app/app 新代码（healthz providers + 多轮 + 延迟实测佐证）；但调试脚本若不显式 insert /app 会导入旧副本——评估脚本均有正确 insert，结果可信。

## 自主改进循环 Round 11（2026-08-16）

- **ai-service 双副本隐患根治**：删除镜像遗留的 site-packages/app 旧副本 + 容器内加 /app .pth + Dockerfile 改 editable 安装（pip install -e .）——从此任何 Python 进程（uvicorn/评估/调试脚本）都解析到 /app/app 单一事实源；决定性验证：删旧副本重启后 uvicorn 仍提供新 healthz，冒烟 10/10。

## 自主改进循环 Round 11 补充

- **轻量评估冒烟集**（5 题/2 分钟）：日常回归工具，本次实测 agentic 忠实度 1.0、拒绝 1.0、引用 1.0。

## 自主改进循环 Round 12（2026-08-16）

- **CI 缓存**：Gradle + pip 缓存启用（加速 CI 运行）。
- **多轮历史回归测试**：ChatServiceTest 断言 AiRagRequest.conversationHistory 携带最近对话（后端 106/106）。
- 连接池复核：Hikari 默认 10 vs PG max_connections 50，余量充足无需调整。

## 自主改进循环 Round 13（2026-08-16）

- **TLS 关键验证（真机可用性红线）**：① debug manifest 的 networkSecurityConfig 正确合并（自签证书信任生效，此前误判为缺失）；② APK 内置 server_cert.pem 指纹与服务器当前证书完全一致——**App 真机可直连服务器**。
- **README 增补安全章节**（认证/防滥用/AI 安全/文件/隐私）。
- 索引复核：chat/report/job 查询索引覆盖充分，当前规模无需新增。

## 自主改进循环 Round 14（2026-08-16）

- **App 设置页显示版本 + 环境标签**（用户可确认安装版本），APK 已更新。
- **架构文档刷新**：JVM 17→21、Flyway V1-V8→V1-V11、1536 维 HNSW→2048 维无 ANN（超 HNSW 上限，说明取舍）、新增 AI 服务当前能力附录（混合检索/自校正/多轮/评估/运维）。

## 自主改进循环 Round 15（2026-08-16）

- **报告解读"重新解读"功能**：POST /api/v1/reports/{id}/interpret（共享 ReportInterpretationService：查询+重试+解析，worker 与新端点共用）+ App 详情页"重新解读"按钮——解读缺失/过时时用户可自助重新生成。生产验证：重新生成 10014 摘要+2 发现+1 建议；冒烟 10/10。
- 后端 106 全绿。
