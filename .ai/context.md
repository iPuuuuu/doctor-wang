---
name: doctor-wang-ai-context
description: 项目目标、仓库结构与 AI 协作上下文
metadata:
  type: project
---

# Doctor-Wang AI Team Context

## Product

Doctor-Wang 为个人用户提供健康咨询、医疗报告整理与健康档案辅助能力。系统必须明确“健康辅助”与“医疗诊断”的边界，不能替代医生或急诊服务。

## Repository map

```text
app/                         Android Kotlin Compose 客户端
doctor-wang-mock-server/     当前 Spring Boot 后端过渡模块
deploy/                      Docker Compose 与 Nginx 部署资产
docs/                        项目文档
.ai/                         AI 团队角色、工作流和上下文
```

## Current capabilities

- Android 已有登录、首页、聊天、报告上传入口、报告详情、健康档案和设置页面。
- 后端已有登录、Bearer session、聊天历史、健康档案、报告记录和 Flyway/PostgreSQL 持久化。
- 当前聊天是关键词规则回复，报告上传只提交文件名并生成演示结果。
- 仓库没有独立 Python FastAPI/LangGraph 服务。
- 仓库目前没有自动化测试文件；任何新功能必须补测试。

## Target direction

```text
Android → Spring Boot API v1 → FastAPI/LangGraph AI Service
                                  ├─ LLM Provider
                                  ├─ RAG / Vector Store
                                  ├─ OCR / Document Parsing
                                  └─ Safety / Evaluation
```

## Engineering facts

- 当前后端实际为 Kotlin/JVM 17；项目目标写明 Java 21。升级前必须做 ADR、构建验证和兼容性说明。
- 当前数据库为 PostgreSQL；不得把 MySQL 当作既定事实引入，除非用户明确批准迁移。
- 数据库使用 Flyway，`ddl-auto` 应保持校验模式；任何 schema 变更都必须新增 migration。
- 生产部署不能使用明文 HTTP；Android release 不得允许 cleartext traffic。

## Medical safety baseline

每个面向用户的 AI 输出都要考虑：危险信号、特殊人群、不确定性、就医建议、证据来源、隐私和审计。不得编造检查结果、药物剂量、医生身份或医学来源。
