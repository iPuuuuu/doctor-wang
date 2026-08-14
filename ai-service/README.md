# Doctor-Wang AI Service

独立的 FastAPI/LangGraph AI 处理服务，当前只正式支持文本型 PDF、JPG 和 PNG。扫描版 PDF 保留 OCR 扩展点，低置信度结果进入 `REVIEW_REQUIRED`，不会被当作可靠 RAG 来源。

```bash
cd ai-service
python -m venv .venv && . .venv/bin/activate
pip install -e '.[dev]'
uvicorn app.main:app --reload
pytest
```

服务不接受 Android token，也不负责用户授权；生产环境由 Spring Boot 通过受控内部网络调用，并使用 `X-Internal-Token`。未配置模型或 PostgreSQL 时可使用 fake provider 和内存向量库进行离线测试。

`PgVectorStore` 使用后端批准的 `ai_vector_chunk` 表（`namespace`、`chunk_id`、`content`、`embedding vector`、`metadata jsonb`，并以 `(namespace, chunk_id)` 唯一约束）；本次不会创建或修改该表的 Flyway migration。`DW_AI_OBJECT_STORE_ROOT` 配置 `objectKey` 的本地开发根目录，job state 默认保存到 SQLite 文件；这些都是开发态实现。

`POST /internal/v1/ingestions/{jobId}/retry` 只在 job 为 `FAILED` 或 `REVIEW_REQUIRED` 且存在脱敏请求元数据时重放；原始文件内容不会写入 job state。该接口仍是开发态契约，生产需要持久化 job store、幂等审计和服务级认证。

可配置 provider：

- `DW_AI_EMBEDDING_ENDPOINT`：OpenAI-compatible `/embeddings` endpoint
- `DW_AI_LLM_ENDPOINT`：OpenAI-compatible `/chat/completions` endpoint
- `DW_AI_OCR_ENDPOINT`：返回 `{ "pages": [{"pageNo": 1, "text": "...", "confidence": 0.9}] }` 的 OCR endpoint
- 对应的 `*_API_KEY` 只从环境变量读取，不写入日志或仓库

P2 接入契约、错误码、状态机、对象存储生命周期、job state 迁移和 pgvector migration 建议见 [`docs/superpowers/specs/2026-08-08-ai-retrieval-p2-production-readiness.md`](../docs/superpowers/specs/2026-08-08-ai-retrieval-p2-production-readiness.md)。该文档是生产化准备方案，不表示当前服务已达到生产可用标准。
