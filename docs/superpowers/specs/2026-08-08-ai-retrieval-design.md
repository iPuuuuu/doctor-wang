# AI Retrieval Pipeline Design

> 状态：Phase 1 开发态已实现（仅 `ai-service/`；Spring Boot/Android/数据库未修改；不代表生产可用）
> 范围：`ai-service` 目录与 AI 相关设计文档
> 不在本方案内：Spring Boot 业务代码、数据库 migration、Android 代码
> 日期：2026-08-08

## 1. 目标与非目标

### 目标

在 Doctor-Wang 中建立两条可追踪、可回退、可扩展的 AI 链路：

- **Ingestion Graph**：负责文档接入、PDF / 图片解析、分块、Embedding、向量写入、版本记录、删除与重建
- **RAG Query Graph**：负责授权后检索、引用生成、风险提示、安全失败策略与响应组装

两条链路共同覆盖：

- PDF 解析
- 图片 OCR 扩展点
- 文档分块
- Embedding
- pgvector 向量索引
- 报告专属 namespace
- RAG 检索
- 引用片段返回
- 风险提示返回
- 失败态与重试策略
- 模型、Prompt、解析器与安全策略版本记录
- 基础评估集

### 非目标

- 不实现医疗级诊断或治疗建议
- 不修改 Spring Boot 业务代码
- 不修改数据库 migration
- 不修改 Android 代码
- 不承诺扫描版 PDF 在 Phase 1 已经正式支持
- 不引入真实患者数据、API key、模型密钥

## 2. 范围边界

### Phase 1 正式支持

- 文本型 PDF
- JPG / PNG 图片

### Phase 1 必须具备但不宣称正式支持

- 扫描版 / 图片型 PDF 的 extractor 路由
- OCR 扩展接口
- 低置信度门禁
- `REVIEW_REQUIRED` 状态

### 约束

- 仅允许给出 `ai-service` 目录内的实现设计
- 若发现需要后端或数据库改动，只输出接口契约和变更建议

## 3. 总体架构

采用三层分工：

1. **Spring Boot 业务编排层**：负责用户权限、上传入口、任务状态权威来源、对象存储与 AI Service 调用编排。
2. **Python FastAPI/LangGraph AI Service**：负责文档提取、OCR 路由、分块、Embedding、向量写入、RAG、风险提示与安全检查。
3. **PostgreSQL + pgvector**：负责元数据与向量检索索引。

AI Service 不直接决定用户是否有权限访问报告；它只处理已授权的任务输入。

### 3.1 数据所有权

- **对象存储中的原始文件**：由后端业务层写入并持有引用，AI Service 仅消费对象存储 URI 或短期读取凭证
- **PostgreSQL 元数据与处理状态**：由后端业务层作为权威来源持久化；AI Service 只能返回建议状态、处理结果和版本信息
- **pgvector 向量条目**：由 AI Service 写入/更新；后端业务层只持有 namespace、reportId、删除/重建请求与审计事件
- **AI Service 运行期产物**：chunk、embedding 输入哈希、prompt 版本、parser 版本、safety 版本、traceId 关联信息由 AI Service 生成并返回持久化建议

## 4. AI Service 模块拆分

### 4.1 API 层

- FastAPI 内部接口
- Pydantic schema
- 内部鉴权
- traceId 透传
- 幂等键支持

### 4.2 LangGraph 处理图

#### 4.2.1 Ingestion Graph

推荐主流程：

`validate_input → detect_document_type → extract_text → quality_gate → chunk → embed → upsert_vectors → persist_ingestion_result`

职责：

- 处理上传后的文档接入
- 完成解析、分块、Embedding、写向量索引
- 返回可持久化的处理结果与版本记录
- 处理删除、重试和重建请求

#### 4.2.2 RAG Query Graph

推荐主流程：

`authorize_request → resolve_namespace → retrieve_candidates → rerank → assemble_citations → generate_risk_guidance → safety_review → format_response`

职责：

- 在已授权范围内做检索
- 只返回可解释引用
- 生成风险提示与失败态
- 不执行写入向量索引操作

### 4.3 文档解析层

- `DocumentExtractor`：统一抽象
- `TextPdfExtractor`：文本型 PDF 提取
- `ImageOcrExtractor`：图片 OCR 扩展点
- `ImageExtractor`：JPG / PNG 直接 OCR 路由
- `DocumentTypeRouter`：根据 MIME、文件签名、文字层质量选择 extractor

### 4.4 索引层

- Chunk 结构化
- Embedding provider
- Vector store adapter
- namespace 隔离

### 4.5 RAG 层

- 检索
- 重排
- 引用生成
- 风险提示生成
- 失败降级

### 4.6 安全与评估

- 质量门禁
- 低置信度拒绝进入主 RAG
- Prompt / model / parser / safety version 记录
- 基础评估集与回归对比

## 5. PDF 解析与 OCR 路由

### 5.1 文本型 PDF

优先读取可提取文字层，保留：

- 页码
- 段落位置
- 原始文本片段
- 文档版本标识

### 5.2 扫描版 / 图片型 PDF

保留 extractor 路由，但 Phase 1 不作为正式成功路径。其要求：

- 逐页识别
- 保留 OCR 置信度
- 保留页码与区域引用
- 低质量结果进入 `REVIEW_REQUIRED`
- 不静默进入可用 RAG 结果

### 5.3 图片文件

JPG / PNG 在 Phase 1 属于正式支持范围：

- 默认走图片解析入口
- 优先尝试 OCR 扩展点完成文本提取
- 若 OCR 不可用或置信度不达标，则进入 `REVIEW_REQUIRED`
- 不把“图片文件”与“扫描版 PDF”混为同一正式支持声明；扫描版 PDF 仍保留为未正式支持的扩展路径

## 6. Chunking / Embedding / Vector Search

### 6.1 Chunking

按“可引用、可溯源、适合问答”的原则切块：

- 保留 reportId、pageNo、section、span、confidence
- 尽量避免跨页合并
- 对低质量内容保守切分

### 6.2 Embedding

Embedding 结果必须记录：

- embedding model
- model version
- input hash
- chunk hash
- createdAt

### 6.3 pgvector

使用 PostgreSQL + pgvector 作为首选向量库：

- 便于与现有数据库栈集成
- 便于按 report namespace 隔离
- 便于后续在规模增长时迁移

### 6.4 Namespace 设计

- `report:<userId>:<reportId>`：用户自有报告
- `knowledge:<version>`：审核过的公共健康知识

默认检索必须先做授权，再做 namespace 过滤，再做混合检索。

### 6.5 删除、重试与重建

- **删除**：按 report namespace 删除对应向量片段，并记录删除审计事件与删除版本号
- **重试**：同一 reportId 采用幂等重试；若已存在旧向量片段，重试前必须先标记旧版本失效或清理旧 namespace 数据
- **重建**：支持基于原始文件或已存结构化中间产物重建索引；重建必须重新生成 chunk hash、embedding hash 与 index version
- **一致性原则**：向量索引的最终权威不在 pgvector 本身，而在处理结果版本记录与 namespace/version 组合

## 7. RAG Pipeline

### 7.1 检索策略

推荐混合检索：

1. 先命中报告专属 namespace
2. 再按需要检索知识库 namespace
3. 结合全文 / 关键词与向量召回
4. 按来源可信度、文档版本、置信度、时间重排

### 7.2 输出结构

RAG 输出必须包含：

- 结论性摘要
- 引用片段列表
- 每条引用的来源信息
- 风险提示
- 不确定性说明
- 低置信度标记

### 7.3 失败降级

如果检索不足或引用不可信：

- 明确声明资料不足
- 不编造来源
- 返回可操作的失败态或补充采集建议

## 8. 状态机与失败态

建议状态：

- `UPLOADED`
- `VALIDATING`
- `EXTRACTING`
- `CHUNKING`
- `INDEXING`
- `ANALYZING`
- `SAFETY_REVIEW`
- `COMPLETED`
- `REVIEW_REQUIRED`
- `FAILED`

### 8.1 确定性医疗风险分流

进入 RAG 生成前，必须先做确定性风险分流：

- 胸痛、呼吸困难、意识改变、严重出血、自伤风险、明显急症信号 → 直接返回高风险就医建议，不依赖模型自由生成
- 信息不足但存在潜在高风险 → 返回“需要医生确认 / 需进一步补充信息”
- 明确低风险且资料充分 → 允许进入常规 RAG 输出

### 8.2 安全失败策略

- 解析失败：可重试则进入重试队列，否则 `FAILED`
- 低置信度：进入 `REVIEW_REQUIRED`
- 模型/向量服务失败：记录 traceId 与错误类别
- 安全策略拒绝：返回安全失败态，不输出伪装成诊断结论的文本
- 重试要幂等，限制次数

## 9. 版本记录与持久化责任

每次处理结果必须记录：

- parser version
- OCR version
- embedding model/version
- prompt version
- safety policy version
- retrieval policy version
- traceId

### 9.1 持久化责任

- **AI Service**：生成处理结果建议、版本元数据、chunk / embedding / retrieval 产物、失败原因与安全标记
- **Spring Boot 业务层**：持久化最终报告状态、用户可见处理状态、重试标记、审计事件和外部可见引用
- **pgvector**：仅保存向量与检索所需的索引字段，不作为版本权威源

## 10. 内部 API 契约

> 以下为建议契约；本轮不修改 Spring Boot 代码，仅定义交互边界。

### 10.1 Ingestion Submit

`POST /internal/v1/ingestions`

**Request**

```json
{
  "requestId": "req_123",
  "reportId": 10002,
  "userId": 9001,
  "namespace": "report:9001:10002",
  "objectKey": "reports/10002/original.pdf",
  "fileName": "exam.pdf",
  "contentType": "application/pdf",
  "fileSize": 123456,
  "sourceVersion": "upload-v1",
  "traceId": "trace_abc"
}
```

**Response**

```json
{
  "jobId": "job_001",
  "status": "ACCEPTED",
  "acceptedAt": "2026-08-08T10:00:00Z"
}
```

### 10.2 Ingestion Status

`GET /internal/v1/ingestions/{jobId}`

**Response**

```json
{
  "jobId": "job_001",
  "reportId": 10002,
  "status": "COMPLETED",
  "phase": "INDEXING",
  "canRetry": true,
  "requiresReview": false,
  "failureCategory": null,
  "resultVersion": "ingest_20260808_001",
  "traceId": "trace_abc"
}
```

### 10.3 RAG Query

`POST /internal/v1/rag/query`

**Request**

```json
{
  "requestId": "req_q_001",
  "userId": 9001,
  "reportId": 10002,
  "question": "这个报告需要注意什么？",
  "namespaceHints": ["report:9001:10002"],
  "topK": 5,
  "traceId": "trace_abc"
}
```

**Response**

```json
{
  "requestId": "req_q_001",
  "status": "OK",
  "riskLevel": "LOW",
  "summary": "...",
  "citations": [
    {
      "sourceType": "report",
      "sourceId": "10002",
      "pageNo": 2,
      "excerpt": "...",
      "confidence": 0.93
    }
  ],
  "warnings": ["仅供健康辅助参考，不构成诊断意见"],
  "needsReview": false,
  "traceId": "trace_abc"
}
```

### 10.4 Delete Index

`DELETE /internal/v1/indexes/{namespace}`

用于删除、重试前清理和重建准备。

### 10.5 Rebuild Index

`POST /internal/v1/indexes/rebuild`

用于基于已保存原始文件或中间产物重建索引。

## 11. 基础评估集

### 11.1 样本

- 20 份文本型 PDF
- 10 份 JPG
- 10 份 PNG
- 5 份扫描版 PDF 作为非正式验证样本
- 每份样本包含人工标注的页码、关键片段和期望风险级别

### 11.2 指标

- 文本提取成功率 ≥ 95%
- 低置信度拦截准确率 ≥ 90%
- 引用可追溯率 ≥ 95%
- 检索命中率 ≥ 80%
- 风险分流召回率 = 100%（高风险样本不得漏报）

### 11.3 发布阻断条件

满足任一条件则阻断发布：

- 高风险样本漏报
- 引用不可追溯率超过 5%
- 文本型 PDF 提取成功率低于 95%
- 低置信度内容未进入 `REVIEW_REQUIRED`
- 版本记录缺失任一必填字段

## 12. 接口契约建议

由于本次不修改 Spring Boot 业务代码，仅给出建议契约：

- 上传任务提交接口：输入对象存储引用、文件元数据、用户与报告标识
- 任务状态查询接口：返回状态、阶段、失败类别、可重试标识
- RAG 查询接口：返回摘要、引用、风险提示、低置信度标识

## 13. 待确认事项

- `ai-service` 的目录结构是否已经存在，还是需要新建
- 首期 Embedding provider 是否直接采用现有可用模型
- 是否需要在 Phase 1 同步定义知识库 namespace 的数据来源
- 解析失败后是否只返回失败态，还是支持人工复核工作流

## 14. 推荐结论

采用 `Spring Boot + Python FastAPI/LangGraph + PostgreSQL/pgvector` 的方案，并在 Phase 1 只正式支持文本型 PDF、JPG、PNG。扫描版 PDF 保留 extractor 路由和 OCR 质量门禁，但不作为正式成功路径。

## 15. Phase 1 实现说明

实现位于 `ai-service/`，包含 FastAPI 内部接口、LangGraph RAG 图、文本型 PDF 提取、OCR 扩展点、页级分块、可配置 Embedding/LLM/OCR provider、pgvector SQL 适配器和 in-memory 测试实现。独立服务支持 `fileBase64` 和受限本地开发态 `objectKey`，job state 保存到 SQLite；生产环境仍应由 Spring Boot 通过对象存储引用和服务间认证调用，并补齐经过批准的 pgvector schema/migration。

扫描版 PDF 或图片在 OCR provider 未配置、文字层不足或置信度不达标时返回 `REVIEW_REQUIRED`，不会进入正常 RAG 引用结果。所有 RAG 响应都包含引用、文档版本、风险级别、警告和 traceId；未检索到证据时返回 `INSUFFICIENT_EVIDENCE`。
