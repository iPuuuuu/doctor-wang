---
id: ADR-0006
title: RAG Pipeline, Safety Gate, and Chat AI Integration
status: accepted
date: 2026-08-12
owners: [architect-agent]
---

# ADR-0006：RAG Pipeline 与安全闸设计决策

## 背景

Chat 端点需要从硬编码关键词回复升级为基于报告的 RAG 问答。AI Service 已有 LangGraph RAG pipeline，但使用 fake embeddings 和 in-memory vector store，缺少真实语义检索能力。同时，医疗安全闸需要覆盖更多中文急诊场景。

## 决策

1. **ChatService 独立于 BackendService** — 从 BackendService 拆出 ChatService（104 行），负责聊天历史和消息发送，支持 AI Service 集成和 mock 降级。
2. **Feature flag 控制 AI 集成** — `AI_SERVICE_ENABLED=true` 时调用 AI Service RAG，否则使用 mock 关键词回复。AI Service 不可用时自动降级到 mock。
3. **RAG 查询以最新报告为上下文** — ChatService 查找用户最新报告，以其 namespace 作为 RAG 查询的 `namespaceHints`。无报告时回退 mock。
4. **安全闸三级制** — HIGH (阻断+急救提示)、MEDIUM (不阻断但 needsReview=true)、LOW (通用免责声明)。术语库扩展至 20 HIGH + 13 MEDIUM，覆盖中英文。
5. **Fake embeddings 用于开发测试** — SHA-256 确定性向量，32 维（可配置至 1536）。真实 embeddings 通过 `DW_AI_EMBEDDING_ENDPOINT` 配置，provider 为 OpenAI 兼容接口。
6. **Vector store 工厂模式** — `create_vector_store(database_url)` 按需选择 InMemoryVectorStore（开发/测试）或 PgVectorStore（生产），后者连接 backend 管理的 `ai_vector_chunk` 表。
7. **Golden evaluation dataset** — 5 个脱敏场景（血检、心电图、糖尿病、空报告、出院小结），10 个查询预期，用于回归测试。

## 取舍

- Fake embeddings 不支持语义检索（所有查询都返回结果），Golden eval 的 `key_terms_in_citations` 断言在 fake 模式下不可用。真实 embeddings 是此功能的 gating item。
- OCR 和 LLM 生成均通过 API key 配置，未配置时优雅降级（OCR → NotImplementedError → REVIEW_REQUIRED；LLM → 静态摘要文本）。
- PgVector wire 代码已就绪但未通过集成测试（无 Docker/PostgreSQL）。

## 风险

- Fake embeddings 缺乏语义区分度 — 所有查询返回相同排序的 chunks，无法验证检索质量。评估门禁依赖真实 embeddings provider。
- InMemoryVectorStore 不持久化 — 服务重启丢失所有索引数据。生产必须切换到 PgVectorStore。
- 安全闸基于关键词匹配 — 可能漏掉非标准表述的高风险症状（如"心口堵得慌"不匹配"胸痛"），也可能误判（如"胸痛已痊愈"）。需持续迭代术语库。
