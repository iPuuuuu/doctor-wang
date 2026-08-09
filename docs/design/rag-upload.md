# 医疗报告上传与 RAG 架构设计

> 状态：待确认设计
> 负责人：Principal Architect
> 日期：2026-08-08
> 范围：Android 文件上传、Spring Boot 报告编排、Python/LangGraph 报告解析与 RAG

## Architecture Decision

### 背景

当前 Doctor-Wang 已有 Android 报告上传入口、Spring Boot 报告接口和 PostgreSQL 报告表，但上传接口只提交 `fileName`，后端使用固定内容创建演示报告。当前没有真实文件存储、OCR、文档解析、Embedding、向量检索或独立 AI Service。

本设计把现有演示链路演进为可追踪的异步处理链路：用户上传真实 PDF/JPG/PNG，系统保存原始文件和处理状态，AI Service 提取文本并建立报告专属索引，最终生成带来源和安全标识的健康辅助解释。

本能力不是医疗诊断。AI 输出必须明确不确定性、资料范围和“需要医生确认”的事项；出现高风险信号时必须优先给出就医分流。

### 当前架构与约束

- Android：Kotlin + Jetpack Compose + ViewModel + Repository + Retrofit + Room + DataStore + Hilt。
- 后端：`doctor-wang-mock-server`，Spring Boot 3 + Kotlin/JVM 17 + Spring Data JPA + Flyway + PostgreSQL。
- 目标后端规则为 Java 21，但本功能不包含未经批准的 Kotlin/JVM 17 到 Java 21 迁移。
- 当前没有 `ai-service`；本设计新增其边界和接口，不在本设计中实现代码。
- 当前数据库使用 PostgreSQL，不切换 MySQL。
- 当前部署为 Docker Compose + PostgreSQL + Nginx，生产 HTTPS、对象存储、队列和监控尚未完成。
- 现有 `medical_report`、`medical_report_finding`、`medical_report_suggestion` 表必须保持兼容；历史 Flyway migration 不得修改。
- 工作区已有未提交修改，本设计不假设这些修改已经合并或稳定。

### 目标

#### 功能目标

1. Android 能够上传真实 PDF、JPG、PNG 文件。
2. 后端能够验证文件、保存元数据、隔离用户数据并创建异步处理任务。
3. AI Service 能够完成 OCR/文本提取、文档分块、Embedding 和索引。
4. 用户能够查看处理状态、摘要、重点发现、建议和证据来源。
5. 后续聊天能够在用户授权范围内检索该用户的报告内容。
6. 每一次解析和回答都能追踪文件、解析版本、模型、Prompt、知识来源和安全检查结果。

#### 非目标

- 不实现医疗级诊断、处方、自动治疗决策或医生替代。
- 不在 Android 端直接调用 LLM、Embedding 或 OCR 服务。
- 不在第一阶段支持手写识别、复杂影像像素级诊断或未经评估的任意文档格式。
- 不在本设计中完成 Java 21 迁移、完整高可用或多租户平台改造。
- 不将用户报告默认写入公共知识库；用户数据和公共医学知识必须隔离。

#### PDF 解析范围前提

本功能必须明确区分两类 PDF：

1. **文本型 PDF**：PDF 内含可选中、可提取的文字层。首阶段应优先支持，使用文本提取器直接获取内容，保留页码和原始文本位置，作为结构化解析和引用的主要来源。
2. **扫描版/图片型 PDF**：PDF 页面本质为图片，没有可靠文字层。需要逐页渲染或提取图片后执行 OCR，并保存 OCR 置信度、页码、区域信息和原始页面引用。

架构上两者统一进入 `DocumentExtractor` 抽象，但处理能力和质量门禁不同：

```text
文件类型识别
  ├─ 存在可靠文字层 → Text PDF Extractor
  └─ 无文字层/文字不足 → OCR Extractor
                                  ↓
                         置信度与质量检查
                                  ├─ 达标 → 继续分块、Embedding、RAG
                                  └─ 不达标 → REVIEW_REQUIRED
```

**推荐分阶段策略：**

- Phase 1：支持文本型 PDF、JPG/PNG 图片；扫描版 PDF 只在 OCR 质量达到验收标准时开放。
- Phase 2：正式支持扫描版 PDF，建立逐页 OCR、低置信度标记、旋转/倾斜处理和人工复核。
- 暂不承诺手写体、复杂表格、低分辨率照片、加密 PDF、医学影像原始文件（如 DICOM）的可靠解析。

如果产品必须在首个版本覆盖扫描版 PDF，则必须在 Phase 1 额外纳入 OCR 服务选型、样本数据集、准确率评估、隐私/跨境审查、处理成本和失败回退设计；不能把“调用 OCR API 成功”视为医疗文本解析质量达标。

### 方案

采用“Spring Boot 业务编排 + Python FastAPI/LangGraph AI 处理 + 对象存储 + PostgreSQL 元数据 + 向量索引”的分层方案。

```text
Android
  │ multipart upload / status polling
  ▼
Spring Boot API v1
  ├─ Auth / user ownership
  ├─ File validation and metadata
  ├─ Report processing job state
  ├─ Object Storage adapter
  └─ AI Service client
          │ internal authenticated request
          ▼
FastAPI + LangGraph AI Service
  ├─ file fetch / malware-safe handoff
  ├─ OCR or text extraction
  ├─ document classification
  ├─ chunking + metadata
  ├─ embedding + vector upsert
  ├─ report extraction
  ├─ RAG retrieval
  └─ safety review / evaluation hooks
          │
          ├─ Object Storage: original and derived artifacts
          ├─ PostgreSQL: metadata, status, structured result, audit
          └─ Vector index: report namespace + approved knowledge namespace
```

#### 处理模式

上传采用异步任务，不让移动端等待 OCR、Embedding 或 LLM 完成：

```text
UPLOAD_RECEIVED
  → VALIDATING
  → STORED
  → EXTRACTING
  → CHUNKING
  → INDEXING
  → ANALYZING
  → SAFETY_REVIEW
  → COMPLETED
```

失败状态为 `FAILED`，需要保存错误类别、可重试标记和 traceId；低置信度或高风险内容进入 `REVIEW_REQUIRED`，不能伪装成已确认结论。

#### RAG 检索边界

RAG 检索分为两个 namespace：

1. `knowledge:<version>`：经审核的公共健康知识、指南和说明文档。
2. `report:<userId>:<reportId>`：用户自己的报告内容，只允许该用户访问。

默认检索策略：先做用户授权和数据归属校验，再做混合检索（关键词/全文 + 向量），最后按来源、文档版本、片段置信度和时间进行重排。检索不到可信来源时，Agent 必须声明资料不足，不得编造引用。

### 模块拆分

#### Android 客户端

建议在现有模块中增量增加以下职责：

- `ReportUploadScreen`：文件选择、类型/大小提示、上传进度。
- `ReportUploadViewModel`：上传任务状态和重试。
- `ReportRepository`：multipart 上传、任务状态查询、报告详情。
- `ReportProcessingUiState`：`idle / uploading / processing / completed / failed / reviewRequired`。
- Room：报告任务和报告摘要缓存，必须带 `userId` 和 `reportId`，不能继续把所有聊天/报告视为单用户。
- 网络层：统一 requestId、401 处理、超时和可恢复错误。

第一阶段可以继续使用轮询；当任务状态稳定后再考虑 Server-Sent Events 或推送通知。

#### Spring Boot Backend

建议按领域拆分现有过宽的 `BackendService`：

- `ReportController`：版本化上传、查询、重试和删除接口。
- `ReportService`：报告归属、生命周期和结构化结果读取。
- `FileStorageService`：对象存储抽象，不让 Controller 依赖具体 S3/MinIO SDK。
- `ReportProcessingJobService`：创建任务、状态迁移、幂等和重试。
- `AiReportClient`：调用 FastAPI 的内部客户端，负责超时、鉴权和 trace 传递。
- `ReportAuditService`：记录访问、下载、解析和 AI 输出审计事件。
- `ReportQueryRepository`：分页、按用户过滤和状态查询。

Spring Boot 是用户身份、权限、业务事实和任务状态的权威来源；AI Service 不直接决定用户是否有权读取报告。

#### AI Service

建议新建独立 Python 服务，职责包括：

- FastAPI 内部接口和 Pydantic schema。
- LangGraph 报告处理图。
- Text PDF 与扫描版 PDF 的 extractor 路由；扫描版 PDF 必须经过 OCR 质量门禁。
- OCR/Text extraction adapter。
- Document classifier。
- Chunker 和 metadata enricher。
- Embedding provider。
- Vector store adapter。
- Structured report extractor。
- Safety reviewer。
- Prompt registry 和 model registry。
- Evaluation runner。

AI Service 接收后端签发的短期内部任务凭证或服务间认证，不接受客户端 token，不自行查询用户数据库。

#### Infrastructure

- Object Storage：生产使用 S3 兼容对象存储；本地开发可使用 MinIO。
- Queue：第一阶段可使用 PostgreSQL job polling；当并发或处理耗时增长时切换 Redis/RabbitMQ/SQS。队列选择应单独做 ADR。
- Vector Store：优先评估 PostgreSQL + pgvector，减少初期运维；超过规模或检索隔离需求后可迁移 Qdrant。
- Observability：Spring Boot 与 AI Service 统一 traceId、结构化日志、指标和错误告警。

### API / 数据模型

API 建议使用 `/api/v1`，当前旧接口保留兼容窗口，不直接删除。

#### 上传

`POST /api/v1/reports`

- Content-Type：`multipart/form-data`
- 字段：文件、可选报告日期、可选报告类型、用户确认的授权范围。
- 服务端必须校验 MIME、扩展名、大小、文件签名、病毒扫描结果和用户配额。
- 返回 `202 Accepted`，不等待 AI 完成。

返回概念：

```json
{
  "reportId": 10002,
  "processingJobId": "job-...",
  "status": "PROCESSING",
  "statusUrl": "/api/v1/reports/10002/status"
}
```

#### 状态

`GET /api/v1/reports/{reportId}/status`

返回：当前状态、进度阶段、失败类别、是否可重试、更新时间和 traceId。不得把内部堆栈或模型原始错误返回给用户。

#### 详情

`GET /api/v1/reports/{reportId}`

返回：报告元数据、处理状态、摘要、重点发现、建议、风险等级、低置信度标记、来源引用和免责声明。

#### 重试

`POST /api/v1/reports/{reportId}/retry`

仅允许当前用户对可重试失败执行；必须幂等并限制重试次数。

#### 删除

`DELETE /api/v1/reports/{reportId}`

删除必须同时处理数据库元数据、对象存储对象、向量片段和缓存，异步清理失败要进入可观测的补偿任务。

#### AI Service 内部接口

- `POST /internal/v1/report-jobs`：提交已验证的对象存储引用和处理版本。
- `GET /internal/v1/report-jobs/{jobId}`：查询 AI 处理结果或失败状态。
- `POST /internal/v1/rag/query`：在后端已完成用户授权校验后执行报告/知识检索。

内部接口必须使用服务间认证、请求签名或 mTLS，并限制网络可达范围。

#### 数据库逻辑模型

保留现有 `medical_report` 及 findings/suggestions 表，通过新增 migration 扩展，不修改历史 migration。

建议新增：

1. `medical_report_file`
   - `id`
   - `report_id`
   - `storage_key`
   - `original_file_name`
   - `mime_type`
   - `file_size`
   - `sha256`
   - `encryption_key_ref`
   - `created_at`

2. `report_processing_job`
   - `id`
   - `report_id`
   - `status`
   - `stage`
   - `attempt_count`
   - `retryable`
   - `parser_version`
   - `model_version`
   - `prompt_version`
   - `error_code`
   - `trace_id`
   - `started_at`
   - `finished_at`
   - `created_at`
   - `updated_at`

3. `medical_report_text`
   - `id`
   - `report_id`
   - `source_file_id`
   - `text_content`（生产环境应加密或采取严格访问控制）
   - `extraction_method`
   - `extraction_confidence`
   - `language`
   - `created_at`

4. `medical_report_chunk`
   - `id`
   - `report_id`
   - `text_id`
   - `chunk_index`
   - `content_hash`
   - `content_ref`
   - `token_count`
   - `embedding_model`
   - `vector_namespace`
   - `created_at`

5. `medical_report_citation`
   - `id`
   - `report_id`
   - `source_type`
   - `source_ref`
   - `quote_or_span`
   - `document_version`
   - `created_at`

6. `ai_audit_event`
   - `id`
   - `user_id`
   - `report_id`
   - `job_id`
   - `event_type`
   - `model_version`
   - `prompt_version`
   - `safety_policy_version`
   - `trace_id`
   - `metadata_json`
   - `created_at`

所有新增表都必须有用户/报告索引、访问权限策略、保留期限和脱敏说明。实际 SQL、索引细节和 pgvector 类型应在实施前由 Backend 和 AI Engineer 联合确认。

### 技术选择与替代方案

#### 文件存储

推荐：S3 兼容对象存储；本地使用 MinIO。

替代：仅使用 PostgreSQL `bytea`。

取舍：数据库存二进制实现简单，但会放大备份、连接和数据库容量压力，不适合报告文件长期存储。

#### 异步任务

推荐第一阶段：PostgreSQL job table + worker polling。

替代：Redis、RabbitMQ、SQS 等消息队列。

取舍：数据库任务表降低初期运维复杂度，并可保持事务一致；当 OCR/Embedding 并发增大时再迁移到专用队列，迁移前需做容量测试。

#### 向量数据库

推荐第一阶段评估：PostgreSQL + pgvector。

替代：Qdrant 或其他专用向量数据库。

取舍：pgvector 可以减少组件数量并复用 PostgreSQL 权限和备份；如果检索规模、过滤复杂度或隔离要求超出 PostgreSQL 能力，再采用 Qdrant。不得在没有基准测试和运维方案的情况下同时引入两套向量存储。

#### OCR / 文档解析

推荐：AI Service 抽象 `DocumentExtractor`，首版支持 PDF 文本提取和常见图片 OCR，具体供应商可配置。

替代：全部由云厂商托管解析。

取舍：抽象层可以降低供应商锁定和便于脱敏；云 OCR 需要额外评估数据跨境、保留、合规和成本。扫描版 PDF 的 OCR 结果必须携带页码、置信度和可定位区域，低质量结果不得静默进入 RAG。

### 安全与医疗风险

#### 文件安全

- 限制文件大小、扩展名、MIME 和 magic number。
- 对 PDF、图片执行病毒/恶意内容扫描。
- 对象存储使用私有 bucket、短期签名 URL 和服务端加密。
- 文件名、报告文本和 OCR 内容不能进入普通日志。
- 下载和删除必须校验当前用户归属。
- 对同一用户设置存储配额和上传频率限制。

#### RAG 隔离

- 用户报告 namespace 必须包含不可猜测的用户和报告标识。
- 每次检索前由 Spring Boot 完成授权，AI Service 不信任客户端传入的 userId。
- 公共医学知识与私人报告索引分离。
- 报告删除必须触发向量删除和对象存储清理。
- 引用必须来自实际检索结果，禁止模型自行生成来源。

#### 医疗安全

- 解析结果标记为 AI 辅助解释，不是诊断。
- 高风险症状和严重异常优先进入安全分流节点。
- 低 OCR 置信度、矛盾字段和超出支持范围的报告进入 `REVIEW_REQUIRED`。
- 输出必须包含资料范围、局限性和建议就医场景。
- 首版禁止自动给出高风险用药、停药、剂量调整和确定性疾病结论。
- 保存模型、Prompt、知识库和 safety policy 版本，支持事后回放。

### 迁移与发布计划

#### Phase 1：真实上传基础

- 新增文件元数据和处理任务表。
- 保留现有 `POST /api/report/upload` 作为兼容接口，但标记为 deprecated。
- 新增 `/api/v1/reports` multipart 接口。
- 接入本地 MinIO 或受控本地存储适配器。
- 先实现上传、校验、状态查询和失败重试，不宣称已完成 AI 解析。

#### Phase 2：文档处理

- 建立 AI Service 和内部任务接口。
- 加入 PDF 文本提取、图片 OCR、结构化结果和安全检查。
- 结果写回现有报告详情模型，新增字段通过兼容 API 暴露。

#### Phase 3：RAG

- 先建立报告专属索引，再接入审核后的公共知识库。
- 通过离线评估集验证召回、引用和安全行为。
- 逐步将聊天上下文接入 RAG，保留 Demo Provider 作为开发 fallback。

#### Migration 原则

- 只新增 Flyway migration，不修改 V1/V2。
- 先添加可空字段/新表，再回填，再切换读写，最后在独立迁移中收紧约束。
- 所有迁移在 staging 使用脱敏数据演练，并验证备份恢复。
- 向量索引和对象存储删除需要补偿任务，不能依赖单个数据库事务完成跨系统删除。

### 影响

#### Android

- Retrofit 从 JSON 文件名请求改为 multipart。
- 增加上传进度、处理状态、失败重试和结果刷新。
- Room 增加用户和报告维度，避免账号切换时数据污染。
- 报告详情增加来源、置信度、风险级别和免责声明。

#### Backend

- 拆分当前 `BackendService` 中的报告逻辑。
- 增加对象存储、任务、审计和 AI client 适配器。
- 增加 API v1、错误码、幂等和状态迁移。
- 新增 Flyway migration 和集成测试。

#### AI Service

- 新建 Python 服务、LangGraph 流程、模型 provider、解析器、Embedding 和 RAG adapter。
- 增加评估集、Prompt registry、模型版本和安全策略版本。

#### Infrastructure

- 增加对象存储、worker、AI Service 和内部网络策略。
- 增加指标、日志、trace、密钥管理和备份策略。

### 风险

| 风险 | 等级 | 说明 | 缓解措施 |
|---|---|---|---|
| OCR 误读医疗数值 | P0 | 可能导致错误解释 | 置信度、原文引用、人工确认、样本评估 |
| 用户报告越权检索 | P0 | 造成健康隐私泄漏 | 后端授权、namespace 隔离、越权测试 |
| LLM 产生确定性诊断 | P0 | 医疗安全和合规风险 | 确定性 triage、输出审查、拒答和就医分流 |
| 跨系统删除不一致 | P1 | 数据残留 | 删除任务、补偿机制、审计和定期清理 |
| 处理任务积压 | P1 | 用户长时间看不到结果 | 状态监控、worker 扩容、超时和重试上限 |
| 向量检索召回错误 | P1 | 解释缺少依据 | 混合检索、来源重排、离线 golden set |
| AI 成本失控 | P1 | 大文件和长上下文造成高成本 | 文件/Token 配额、摘要分层、模型路由 |
| 新旧 API 漂移 | P1 | Android 联调失败 | OpenAPI、contract test、兼容窗口 |
| Java 21 迁移与功能同时进行 | P2 | 增大变更面 | 单独 ADR 和独立发布验证 |

### 验收标准

#### 功能

- 用户可以上传受支持的真实 PDF/JPG/PNG。
- 非法格式、超大文件和未登录请求会被拒绝。
- 上传返回任务 ID，客户端不需要等待同步解析。
- 用户可以查询任务状态、失败原因类别和重试能力。
- 完成后能看到结构化摘要、重点发现、建议、来源和免责声明。
- 用户只能读取、检索和删除自己的报告。

#### AI / RAG

- 解析结果包含原文片段或可定位来源。
- OCR 或结构化抽取低置信度时进入人工确认/待复核状态。
- 无可信检索结果时不编造引用并明确资料不足。
- 高风险症状触发就医分流，不输出确定性诊断。
- Prompt、模型、解析器、知识库和安全策略版本均可审计。

#### 工程

- Backend、AI Service、Android 关键路径都有自动化测试。
- Migration 可在空库和现有脱敏数据上执行。
- API 文档和客户端模型同步更新。
- 生产日志不包含原始报告、OCR 全文、token 或密钥。
- 具备失败重试、超时、traceId、指标和告警。

### 待确认事项

1. 第一阶段对象存储选择：云端 S3 还是本地 MinIO。
2. **PDF 解析范围：第一阶段是否只支持文本型 PDF，还是同时正式支持扫描版/图片型 PDF OCR？** 推荐默认只承诺文本型 PDF；若必须支持扫描版 PDF，需要同步确认 OCR 供应商、准确率目标、隐私/区域限制和成本预算。
3. 第一阶段 OCR 范围和供应商，是否允许第三方处理健康资料。
4. 是否批准 PostgreSQL 安装 pgvector，还是直接采用 Qdrant。
5. 报告原文和 OCR 文本的加密、保留期限、删除和导出策略。
6. 第一阶段支持的报告类型和最大文件大小。
7. AI Service 的部署位置、模型供应商、区域和预算上限。
8. `POST /api/report/upload` 兼容接口的下线时间。
9. 高风险或低置信度报告是否需要人工复核队列，以及由谁处理。

## 实施前决策门

本设计完成后，Architect 角色不继续写业务代码。用户确认上述待确认事项和 Phase 1 范围后，才分别交给 Backend Engineer、AI Engineer 和 QA Engineer 输出实现计划。
