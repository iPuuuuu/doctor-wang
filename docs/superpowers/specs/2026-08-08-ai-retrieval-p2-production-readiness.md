# Doctor-Wang P2：医疗报告上传与 RAG 生产化接入准备

> 状态：方案已输出，开发态准备；不代表生产可用
> 范围：Spring Boot ↔ AI Service 接入契约、存储/任务/pgvector 生产化准备、Provider 验收与医疗安全门禁
> 日期：2026-08-08

## Architecture Decision

### 背景与约束

P1 已在 `ai-service/` 内完成开发态解析、分块、Embedding、RAG 和 provider 抽象，测试为 8 passed。当前 job state 为 SQLite，默认向量库为 in-memory，pgvector 只有适配器，AI Service 尚未由 Spring Boot 接入。

本阶段只固化生产化准备方案和可执行契约：不修改 Java/Kotlin 业务代码、不修改 Flyway migration、不修改 Android 或部署配置，也不把任何开发态能力描述为已上线。

### 目标

1. Spring Boot 能按稳定版本契约调用 AI Service。
2. 明确请求幂等、状态机、错误分类、重试和超时语义。
3. 明确原始文件、签名 URL、病毒扫描、生命周期和删除边界。
4. 为 job state 和 pgvector 提供后端可实施的表契约、迁移顺序和回滚策略。
5. 为 OCR、Embedding、LLM provider 建立验收、fallback、成本和审计要求。
6. 建立医疗安全、脱敏评估集和发布阻断条件。

## 1. 服务边界和数据流

```text
Android
  → Spring Boot（认证、用户归属、文件接收、任务权威状态）
  → Object Storage（原始文件，短期签名读取）
  → AI Service（内部认证、解析、Embedding、pgvector、RAG）
  → Spring Boot（持久化结果、审计、对客户端返回）
```

Spring Boot 是权限和业务状态的唯一权威。AI Service 不接受 Android token，不查询用户数据库，不自行决定用户能否读取报告。

## 2. 内部 API 契约清单

### 2.1 通用请求头

所有内部请求必须携带：

| Header | 要求 |
|---|---|
| `Authorization` 或 `X-Internal-Token` | 服务间认证；生产推荐 mTLS + 短期 JWT |
| `X-Request-Id` | 调用方生成，重试时保持不变 |
| `X-Trace-Id` | 全链路追踪，缺失则拒绝或由网关补齐 |
| `Idempotency-Key` | ingestion/rebuild 必填；同一业务操作保持稳定 |
| `Content-Type` | `application/json` |

### 2.2 Ingestion Submit

`POST /internal/v1/ingestions`

请求字段：

```json
{
  "requestId": "req_123",
  "idempotencyKey": "report-10002-upload-v1",
  "reportId": 10002,
  "userId": 9001,
  "namespace": "report:9001:10002",
  "objectKey": "reports/9001/10002/source-v1/report.pdf",
  "fileName": "report.pdf",
  "contentType": "application/pdf",
  "fileSize": 123456,
  "sha256": "...",
  "sourceVersion": "upload-v1",
  "signedReadUrlExpiresAt": "2026-08-08T10:05:00Z",
  "traceId": "trace_abc"
}
```

生产主路径只接受后端签发的短期对象存储读取凭证或受控 object reference；`fileBase64` 仅保留在开发/测试环境，不得用于大文件生产传输。

成功返回 `202 Accepted`：

```json
{
  "jobId": "job_001",
  "status": "ACCEPTED",
  "phase": "VALIDATING",
  "traceId": "trace_abc",
  "acceptedAt": "2026-08-08T10:00:00Z"
}
```

相同 `Idempotency-Key` 必须返回原 job，不得重复写入向量或重复计费。相同 key 但 payload hash 不同返回 `IDEMPOTENCY_KEY_REUSED`。

### 2.3 Ingestion Status

`GET /internal/v1/ingestions/{jobId}`

```json
{
  "jobId": "job_001",
  "reportId": 10002,
  "status": "COMPLETED",
  "phase": "INDEXING",
  "progress": 0.85,
  "canRetry": false,
  "requiresReview": false,
  "failureCode": null,
  "resultVersion": "ingest-20260808-001",
  "parserVersion": "pdf-parser-1.0.0",
  "embeddingModel": "configured-model-v1",
  "traceId": "trace_abc",
  "updatedAt": "2026-08-08T10:01:00Z"
}
```

### 2.4 Retry

`POST /internal/v1/ingestions/{jobId}/retry`

只允许 `FAILED` 且 `retryable=true` 的任务。重试必须带新的 attempt 记录，但沿用原始业务幂等键的父标识；重试前标记旧 index version 无效，成功后再切换 active version。

### 2.5 RAG Query

`POST /internal/v1/rag/query`

请求必须包含后端已授权的 namespace；AI Service 只校验 namespace 与 `userId/reportId` 的一致性。响应状态固定为：

- `OK`
- `INSUFFICIENT_EVIDENCE`
- `SAFETY_BLOCKED`

响应必须包含 `citations`、`riskLevel`、`warnings`、`needsReview`、`traceId`。无可信检索结果时 `citations=[]`，禁止生成伪造引用。

### 2.6 错误码

| HTTP | `failureCode` | retryable | 说明 |
|---:|---|:---:|---|
| 400 | `INVALID_REQUEST` | 否 | schema、namespace 或 MIME 不合法 |
| 401/403 | `UNAUTHORIZED` / `FORBIDDEN_NAMESPACE` | 否 | 服务认证或用户隔离失败 |
| 404 | `OBJECT_NOT_FOUND` | 否 | object reference 不存在或已过期 |
| 409 | `IDEMPOTENCY_KEY_REUSED` / `JOB_NOT_RETRYABLE` | 否 | 幂等冲突或非法状态转换 |
| 413 | `FILE_TOO_LARGE` | 否 | 超出后端配额 |
| 422 | `UNSUPPORTED_DOCUMENT` / `PARSE_FAILED` | 视错误而定 | 文件签名、解析或 OCR 质量失败 |
| 429 | `RATE_LIMITED` | 是 | provider 或 AI Service 限流 |
| 502/503 | `PROVIDER_UNAVAILABLE` / `VECTOR_STORE_UNAVAILABLE` | 是 | 外部依赖不可用 |
| 504 | `PROVIDER_TIMEOUT` | 是 | provider 超时 |
| 500 | `INTERNAL_ERROR` | 视审计记录而定 | 不向客户端暴露堆栈 |

错误响应不得包含原始文件内容、token、API key、模型堆栈或健康报告敏感片段。

## 3. 状态机与重试语义

```text
ACCEPTED → VALIDATING → EXTRACTING → CHUNKING → INDEXING → COMPLETED
                                      └──────────────→ REVIEW_REQUIRED
任何可重试阶段 ─────────────────────────────────────→ FAILED
```

允许重试的条件：临时网络错误、provider timeout、429、向量库连接失败。不可重试：文件损坏、权限失败、namespace 不匹配、病毒扫描拒绝、格式不支持。

状态迁移必须由后端持久化并做 compare-and-set，AI Service 返回建议状态但不拥有用户可见最终状态。

## 4. 生产对象存储方案

### objectKey

推荐格式：

```text
reports/{userId}/{reportId}/{fileVersion}/{randomId}-{sanitizedFileName}
```

要求：

- 不使用用户原始文件名作为唯一 key
- key 不包含 token 或健康内容
- 后端保存 sha256、MIME、大小、创建时间和 retention 到期时间
- AI Service 只收到短期签名读取 URL 或受控 object reference

### 生命周期

1. 上传到 quarantine bucket
2. MIME/signature 校验和病毒扫描
3. 扫描通过后复制到 encrypted source bucket
4. AI Service 使用短期签名 URL 读取
5. 派生文本/chunk 按 retention 策略保存或加密
6. 删除报告时异步删除原文件、派生产物、向量和缓存
7. 删除失败写入补偿任务并告警

病毒扫描未完成时不得提交 ingestion。签名 URL 建议有效期 5 分钟以内，禁止写入日志。

## 5. Job State 生产模型建议

本轮不新增 migration；建议后端后续新增 `report_processing_job` 唯一约束：

```text
(report_id, idempotency_key) UNIQUE
```

字段建议：

- `job_id`, `report_id`, `user_id`
- `status`, `phase`, `attempt_count`, `retryable`
- `idempotency_key`, `payload_hash`
- `parser_version`, `ocr_version`, `embedding_model`, `prompt_version`
- `failure_code`, `trace_id`
- `active_index_version`, `created_at`, `updated_at`, `finished_at`

SQLite job state 迁移建议：先双写/校验 → 后端表成为读取权威 → 稳定后关闭 SQLite 写入。迁移期间按 `job_id` 去重，失败任务保留可重放 payload，不迁移原始敏感文件到日志。

## 6. pgvector 落地准备

### 表契约

```sql
CREATE TABLE ai_vector_chunk (
  id BIGSERIAL PRIMARY KEY,
  namespace TEXT NOT NULL,
  chunk_id TEXT NOT NULL,
  report_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  content_hash CHAR(64) NOT NULL,
  embedding VECTOR(<approved_dimensions>) NOT NULL,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  index_version TEXT NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(namespace, chunk_id, index_version)
);
```

索引建议：

- B-tree `(namespace, active)`
- HNSW 或 IVFFlat embedding index，需用批准模型维度和距离函数验证后选择
- `report_id`, `user_id` 仅作额外过滤，不替代 namespace 授权

### 删除和重建

- 删除：先将 namespace 的 `active=false`，再异步物理删除
- 重建：写入新 `index_version`，校验 chunk 数和 embedding 数后原子切换 active version
- 重试：禁止直接覆盖 active version；保留旧版本直到新版本验收完成
- 回滚：将 active version 指针切回上一版本，再清理失败版本

### Migration 建议

后端批准后新增 Flyway migration，不修改历史 migration。发布前检查 pgvector extension、维度、索引构建时间和回滚窗口；回滚只撤销新索引/新版本，不删除仍被 active version 引用的数据。

## 7. Provider 生产化验收

### 通用要求

- connect/read timeout
- 有限次数 exponential backoff
- 429/5xx 分类和 retry-after 支持
- 每请求 `traceId`, provider, model, latency, token/字符计量
- API key 只来自 secret manager/environment
- 请求和响应日志默认不记录原文、prompt、报告片段或密钥

### OCR

- 文本型 PDF 不应调用 OCR
- 扫描样本逐页返回文本和 confidence
- 低于阈值必须 `REVIEW_REQUIRED`
- 旋转、空页、乱码和超时均有明确错误码
- 以脱敏标注集评估页级文本准确率和低置信度拦截率

### Embedding

- 维度与 pgvector schema 严格一致
- 相同输入和 model version 结果可重现到允许误差
- provider timeout 不写入半成品 active index
- 记录 input hash、chunk hash、model/version 和成本

### LLM

- 仅允许从检索 context 生成
- 结构化响应校验失败必须安全降级
- prompt injection、无证据问题、高风险问题必须通过回归集
- 记录 model、prompt、safety policy version、token 使用和 latency
- provider 不可用时返回 `INSUFFICIENT_EVIDENCE` 或安全失败，不编造回答

## 8. 医疗安全与评估

评估集必须使用脱敏或合成材料，至少包括：

- 正常文本型 PDF、JPG、PNG
- 扫描版 PDF 和低清晰度图片
- 胸痛、呼吸困难、意识改变、严重出血、自伤风险
- 报告中不存在答案的问题
- 恶意 prompt injection 和跨用户 namespace 请求

验收指标：

- 高风险分流召回率 100%
- 引用可追溯率 ≥ 95%
- 无证据问题伪造引用率 0%
- namespace 越权成功率 0%
- 文本型 PDF 提取成功率 ≥ 95%
- 低置信度结果进入 `REVIEW_REQUIRED` 的准确率 ≥ 90%

发布阻断条件：高风险漏报、越权检索、伪造引用、密钥/健康数据泄漏、版本字段缺失、provider 失败后仍输出确定性医疗结论。

## 9. 接入实现顺序

1. Spring Boot 先实现内部 HTTP client、服务认证、request/trace/idempotency header 和契约测试。
2. 后端落地 `report_processing_job`，以 CAS 管理状态和重试。
3. 接入 quarantine/source object bucket、病毒扫描和短期签名 URL。
4. 新增 pgvector extension、`ai_vector_chunk` migration、索引和回滚脚本。
5. AI Service 切换到生产 job store 和 PgVectorStore，保留 in-memory 仅测试。
6. 分别验收 OCR、Embedding、LLM provider，加入 timeout、限流、成本和审计指标。
7. 运行脱敏医疗评估集和安全回归；未满足阻断条件前不得发布。

## 10. 待批准事项

- pgvector 扩展、embedding 维度和 HNSW/IVFFlat 选择
- 对象存储供应商、区域、加密和 retention
- 任务队列/worker 方案
- OCR、Embedding、LLM provider 和数据跨境审查
- Spring Boot 接入该契约的实现排期
