# P3 后端 AI Service 接入冻结与最终发布阻断清单

当前结论：**NOT PRODUCTION READY / BLOCKED**。

本文件是发布判定的阻断清单，不是上线批准书。除非“最终发布清单”全部具备可审计的通过证据，否则不得将任何 P3 能力描述为“生产可用”“可上线”或“已完成生产接入”。

外部依赖验证的执行步骤、最小验收输入和证据模板见 [RELEASE_VALIDATION_CHECKLIST.md](RELEASE_VALIDATION_CHECKLIST.md)。该 checklist 中任一项未完成、未签字或无法复现，均不得解除本文件的 `BLOCKED` 结论。

本轮只冻结 Spring Boot 侧的生产接入边界，不代表 AI Service、对象存储、向量库或整体系统已经具备上线条件。

已落地的基础边界：

- 内部 HTTP client，统一传递 `X-Request-Id`、`X-Trace-Id`、`Idempotency-Key`；开发态可使用 internal token。
- PostgreSQL `report_processing_job` 作为权威任务状态，包含用户、phase、payload hash、trace 和 active index version 字段，并提供 CAS 更新入口。
- 文件签名校验、隔离存储 key、短期 HMAC signed URL 生成接口和病毒扫描替换点。
- pgvector 新 migration、用户/报告/namespace 唯一约束和 HNSW 索引准备。
- namespace 越权的后端拒绝测试和 AI client 相关性头契约测试。

明确的 dev-only / blocked 项：

- `NoopVirusScanner` 仅为开发态替身，生产必须接入经审查的扫描器。
- `LocalFileStorageService` 仅用于开发/单机环境，生产必须替换为私有 quarantine/source bucket、加密和真正的签名 URL。
- pgvector migration 依赖 PostgreSQL 安装 `vector` 扩展；未验证扩展、维度、索引性能和备份恢复前不得发布。
- AI Service 当前仍是开发态服务；未完成 mTLS/短期服务 JWT、超时/熔断、真实对象存储读取和生产 job store 前不得发布。
- AI Service 当前开发态没有独立 retry 内部接口；后端已冻结 `/internal/v1/ingestions/{jobId}/retry` 客户端契约，但该契约未得到 AI Service 实现验证。
- 尚未具备脱敏评估集和门禁结果；高风险召回率、引用可追溯率、越权成功率和伪造引用率未达标前禁止发布。
- 删除对象、向量索引和缓存的跨系统补偿任务尚未完成。

## 门禁收敛（当前证据）

| 门禁 | 目标 | 当前状态 | 发布结论 |
|---|---:|---|---|
| 高风险分流召回率 | 100% | 未执行脱敏评估集 | BLOCKED |
| 引用可追溯率 | ≥95% | 仅有后端 fail-closed 单元测试 | BLOCKED |
| 越权成功率 | 0% | namespace 回归测试已覆盖基础路径，未完成端到端测试 | BLOCKED |
| 伪造引用率 | 0% | 后端拒绝未知来源，未完成模型评估 | BLOCKED |
| provider 不可用 | 安全失败 | 后端映射为失败状态，未完成生产故障演练 | BLOCKED |
| pgvector migration | 目标库通过 | 未在带 vector 扩展的目标库演练 | BLOCKED |
| 对象存储/病毒扫描 | 生产实现 | 当前为本地存储和 NoopVirusScanner | BLOCKED |
| 服务认证 | mTLS + 短期 JWT | 当前仅 internal token | BLOCKED |
| AI Service 内部 retry | 服务端实现并端到端验证 | 仅冻结后端 client 契约 | BLOCKED |
| 跨系统删除 | 对象、向量、缓存具备补偿与审计 | 尚未实现/演练 | BLOCKED |
| 多实例任务处理 | CAS、租约恢复、超时、容量演练通过 | 仅后端代码/单元测试级验证 | BLOCKED |

本轮自动化验证只证明后端契约边界和 fail-closed 逻辑可编译、可测试，不证明任何外部依赖的生产可用性。

## 本轮测试覆盖与证据边界

- AI ingestion submit：请求头、internal token、幂等键；
- ingestion status：版本化路径和 trace 头；
- ingestion retry：版本化路径和幂等键；
- RAG query：版本化路径和请求关联头；
- delete index：namespace 删除路径；
- 高风险结果：保留 `needsReview`；
- 无证据结果：保留 `needsReview`；
- 无引用的 `OK` 结果：拒绝；
- 未授权 citation 来源：拒绝；
- 用户 namespace 越权：拒绝。

这些测试是后端边界测试，不是生产联调测试或评估集结果。特别是：mock HTTP 契约测试不证明 AI Service 实际支持该 endpoint；单元测试不证明对象存储、病毒扫描、pgvector、mTLS、服务 JWT、故障恢复或模型安全表现已通过生产验证。

## 最终发布清单（全部必需）

以下项目必须逐项附上负责人、执行日期、环境、可复现命令/报告链接和审核结论；任何一项缺失、失败或证据过期，发布状态必须维持 **BLOCKED**。

- [ ] AI Service 生产部署使用 mTLS 和短期服务 JWT；完成密钥轮换、过期、拒绝和审计测试。
- [ ] 真实 quarantine bucket → 安全扫描 → source bucket 流程完成；bucket 私有、服务端加密、最小权限和短期 presigned URL 均经验证。
- [ ] 生产病毒扫描器已替代 `NoopVirusScanner`，并定义扫描器不可用时的 fail-closed 行为。
- [ ] `V5`/`V6` 在目标 PostgreSQL + pgvector 环境完成空库、存量库、回滚、备份恢复和索引性能演练。
- [ ] AI Service 使用生产 job store、真实对象存储和 PgVectorStore；不再依赖 in-memory/SQLite/fake provider 作为生产路径。
- [ ] ingestion submit/status/retry、RAG query、delete index 已在受控集成环境完成端到端契约测试。
- [ ] 删除报告时，对象、向量、缓存和审计记录的补偿任务经失败注入和重复执行验证。
- [ ] 多实例 worker 的 CAS、租约抢占/过期恢复、超时、重试上限、积压和容量演练通过。
- [ ] 脱敏评估集完成并留存版本化报告：高风险分流召回率 100%、引用可追溯率 ≥95%、越权成功率 0%、伪造引用率 0%。
- [ ] 高风险、无证据、低置信度、provider 不可用和 namespace 越权均验证为安全失败或 `REVIEW_REQUIRED`，不返回确定性医疗诊断。
- [ ] 生产日志、trace、审计、告警和健康数据脱敏审查通过；日志中不含报告原文、OCR 全文、token 或密钥。
- [ ] 安全、隐私、医疗安全、后端和发布负责人书面批准发布结论。

## 冻结结论

截至本次冻结，以上最终发布清单没有任何一项被标记为已完成的生产证据。因此 P3 只能描述为“后端生产接入基础边界已冻结”，**不能**描述为生产接入完成；发布状态必须保持 **NOT PRODUCTION READY / BLOCKED**。
