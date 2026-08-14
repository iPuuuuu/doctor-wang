---
name: doctor-wang-ai-context
description: 项目目标、仓库结构与 AI 协作上下文
metadata:
  type: project
  updated: 2026-08-12
---

# Doctor-Wang AI Team Context

## Product

Doctor-Wang 为个人用户提供健康咨询、医疗报告整理与健康档案辅助能力。系统明确"健康辅助"边界，不替代医生或急诊服务。

## Repository Map

```text
app/                         Android Kotlin Compose 客户端
doctor-wang-mock-server/     Spring Boot 3 后端 (Kotlin/JVM 17)
ai-service/                  Python FastAPI + LangGraph AI Service
deploy/                      Docker Compose 与 Nginx 部署资产
docs/                        项目文档
.ai/                         AI 团队 Harness (roles, workflows, state, decisions)
```

## Current Capabilities (as of 2026-08-12)

- Android: 登录、首页、聊天、报告上传、报告详情、健康档案、设置页面
- Backend: JWT 认证 (BCrypt + revocable session)、报告 CRUD (V1 multipart upload)、用户管理 (注册/资料修改/密码修改)、文件下载、健康档案、聊天 (mock + AI Service feature flag)
- AI Service: FastAPI RAG pipeline (ingestion, query, vector store, safety gate)
- Database: 16 张表, Flyway V1–V8, PostgreSQL
- Tests: 57 backend tests (unit + contract), 9 AI pytest, Android unit NO-SOURCE
- Deploy: Docker Compose (PostgreSQL + backend + AI Service + Nginx TLS)

## Target Architecture

```text
Android → Spring Boot API v1 → FastAPI/LangGraph AI Service
                                  ├─ LLM Provider
                                  ├─ RAG / Vector Store
                                  ├─ OCR / Document Parsing
                                  └─ Safety / Evaluation
```

## Engineering Facts

- Backend: Kotlin/JVM 17, Spring Boot 3.3.3, JPA/Hibernate, Flyway, PostgreSQL
- AI Service: Python FastAPI, LangGraph, pgvector (planned), SQLite job state
- Database: PostgreSQL, `ddl-auto: validate` — all schema changes via Flyway migration
- Android: Kotlin, Jetpack Compose, MVVM, Retrofit, Room, DataStore, Hilt
- TLS: Nginx termination, HTTP → HTTPS redirect, cert via volume mount
- Feature flags: `AI_SERVICE_ENABLED` controls Chat RAG integration

## Medical Safety Baseline

Every user-facing AI output must consider: emergency signs, special populations, uncertainty, medical disclaimer, evidence traceability, privacy, and audit trail.
Never fabricate test results, drug dosages, doctor identity, or medical sources.
