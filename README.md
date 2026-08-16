# Doctor-Wang · 健康档案与 AI 健康助手

Doctor-Wang 是面向个人的**健康档案管理 + AI 健康辅助**平台：上传检查报告、AI 解读、问诊咨询，以及针对"疑病症/大病焦虑"的**安心话聊**。

> ⚠️ 健康辅助边界：本应用不提供诊断、处方或急诊替代；所有 AI 输出均带医疗边界声明与可追溯引用。如遇胸痛、呼吸困难、意识改变、严重出血等紧急情况，请立即就医（120）。

## 架构

```text
Android (Kotlin + Compose) ──HTTPS──▶ Spring Boot 后端 ──internal──▶ FastAPI/LangGraph AI Service
                                      │ PostgreSQL + pgvector         ├─ LLM (DeepSeek)
                                      │ Flyway migrations             ├─ Embeddings (智谱)
                                      │ JWT + revocable sessions      └─ OCR (通义 qwen-vl-ocr)
```

- **Android**（`app/`）：登录/注册、首页、AI 问诊、**安心话聊**、报告上传/列表/详情、健康档案、账号设置。Material3 定制主题（镇静青绿）。
- **后端**（`doctor-wang-mock-server/`）：Spring Boot 3 + Kotlin，运行于 Java 21（ADR-0007）。JWT 认证（可撤销会话）、报告 CRUD/上传/下载（MIME+magic number+病毒扫描 fail-closed）、健康档案、聊天与安心话聊、actuator 健康检查。
- **AI 服务**（`ai-service/`）：FastAPI + LangGraph。**自校正 Agentic RAG**（生成→声明级证据校验→回溯修正的 quality gate，未核实内容 fail-closed 标记复核）、**混合检索（字符二元组词法 + 向量 RRF 融合）**、3 级医疗安全门、**anxiety 模式**（安心话聊专用提示词 + 症状自查知识库）、引用追溯、**RAGAS 风格评估体系（忠实度/证据覆盖/相关性/拒绝正确性，支持 single vs agentic 引擎对比）**。
- **部署**（`deploy/`）：Docker Compose（PostgreSQL+pgvector / backend / ai-service / Nginx TLS）、runbook、服务器加固与恢复脚本、CI（GitHub Actions，含 Testcontainers 集成测试）。

## 功能

| 功能 | 说明 |
|---|---|
| 报告上传与 AI 解读 | 上传 PDF/JPG/PNG（≤20MB）→ 通义 OCR → 智谱 embedding → DeepSeek 基于报告内容解读，附引用 |
| AI 问诊 | 结合健康档案与报告回答症状咨询（安全门拦截高风险） |
| **安心话聊** | 疑病症/大病焦虑缓解：结合真实档案+报告，区分"检查结果"与"担忧"，温和循证安抚，建议就医/心理科；安全门与医疗边界始终生效 |
| 健康档案 | 既往史、体征、BMI 概览 |
| 认证安全 | JWT（HS256）+ 服务端可撤销会话、BCrypt、release HTTPS 强制、无敏感日志 |

## 本地开发

```bash
# Android（单测 + 构建）
./gradlew :app:testDebugUnitTest :app:assembleDebug

# 后端（JDK 21；单元/契约测试）
cd doctor-wang-mock-server && ./gradlew test

# AI 服务
cd ai-service && pip install -e ".[dev]" && pytest

# 端到端（需本地 PostgreSQL+pgvector 或 Docker）
# 详见 deploy/RUNBOOK.md 与 .ai/handoffs/DEPLOY-001/
```

## 测试与 CI

- 后端 105 单测/契约测试 · AI 132 pytest · Android 19 契约测试（全绿）
- CI（GitHub Actions）：Android 构建、后端 JDK21 测试、**PostgreSQL/Testcontainers 集成测试**、AI pytest、whitespace 检查
- 关键测试点：JWT/会话/越权、报告归属（namespace）、AI 安全门（高风险/无证据/引用可追溯）、病毒扫描 fail-closed、安心话聊提示词与边界

## 部署

- 单机：`deploy/RUNBOOK.md`（.env 清单、TLS、验证）
- 服务器恢复（1.6GB VPS OOM 事故后）：`deploy/recover-server.sh` + `setup-server.sh`（swap、内存限制）
- 生产环境要求：域名 + 受信任证书（当前自签证书仅限调试）、ClamAV 病毒扫描启用、监控告警（DEPLOY-001b）

## 文档

- 架构与决策：`docs/`、`.ai/decisions/`（ADR-0003/0005-0009）
- API 参考：`docs/api-reference.md`
- 项目状态：`.ai/state/`
