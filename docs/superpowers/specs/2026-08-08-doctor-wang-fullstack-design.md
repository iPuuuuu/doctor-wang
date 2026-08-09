# Doctor-Wang Full-Stack Architecture Design

> 状态：Design Draft
> 范围：全栈协作架构、Backend 基础能力、AI Service、Android 对接、QA 门禁
> 不在本方案内：业务代码实现、数据库实际迁移、Android 代码修改、后端代码修改
> 日期：2026-08-08

## 1. 背景

Doctor-Wang 是健康辅助平台，不是诊断替代品。仓库当前已经具备 Android 客户端、Spring Boot 过渡后端、部署资产和 AI 占位目录，但协作方式、模块边界和实施顺序仍需要统一。

本设计的目标是把当前项目拆成可并行、可验收、可回退的分阶段工作流，确保后续 Backend、AI、Android 和 QA 能按同一套契约推进。

## 2. 当前架构与约束

### 当前事实

- Android：Kotlin + Jetpack Compose + ViewModel + Repository + Hilt
- 后端：`doctor-wang-mock-server`，Spring Boot 3 + Kotlin/JVM 17 + PostgreSQL + Flyway
- AI Service：尚未独立成型为 FastAPI/LangGraph 服务
- 文档与协作层：`.ai/` 已承载角色、上下文、任务板和工作流
- 文档层：`docs/` 已有 architecture、roadmap、technical decisions

### 核心约束

- 不能先实现后设计
- 不能把 mock 能力包装成生产 AI 能力
- Android 不得直连模型供应商
- 健康数据、token、报告内容不能写入日志或仓库
- 数据库 schema 变更必须通过 Flyway migration
- 涉及医疗、安全、隐私、API、数据库的变更必须显式说明风险
- 不引入无必要依赖，不做无关重构

## 3. 目标

1. 建立清晰的全栈职责边界
2. 先定义 API 与数据契约，再做实现
3. 将 AI 能力拆成独立服务：Safety / RAG / Agent / Evaluation
4. 让 Android 仅依赖稳定后端契约
5. 为后续测试与审查建立统一门禁

## 4. 方案

### 方案 A：契约优先、分阶段演进（推荐）

先做总体架构与接口契约，再拆 Backend、AI、Android、QA 的独立实施项。Backend 先稳定 API v1 和会话/数据边界，AI Service 再逐步接入 RAG 与 Agent，Android 最后按契约适配。

**优点**：风险最小，便于并行，便于验收。  
**缺点**：前期需要更多设计与对齐成本。

### 方案 B：先后端，再 AI，再 Android

先把后端全部收敛好，再引入 AI 服务，最后统一改 Android。

**优点**：后端边界更稳。  
**缺点**：AI 和 Android 会被后置，整体周期更长。

### 方案 C：三端同步推进

Backend、AI、Android 同时动手，靠联调修正契约。

**优点**：看起来最快。  
**缺点**：耦合最高，最容易返工，不适合当前阶段。

**推荐结论**：采用方案 A。

## 5. 模块拆分

### 5.1 协作与文档层

- `.ai/roles/*`：角色定义
- `.ai/workflows/*`：协作流程
- `.ai/project-state.md`：当前阶段
- `.ai/task-board.md`：阶段任务
- `docs/architecture.md`：架构原则
- `docs/roadmap.md`：阶段路线图
- `docs/technical-decisions.md`：决策记录

### 5.2 产品能力层

#### Backend Foundation

职责：认证、会话、报告/健康档案数据契约、错误模型、审计、对 AI Service 的内部调用边界。

#### AI Service

职责：RAG、Agent、Safety、Evaluation、提示词与模型编排，不直接持有用户权限决定权。

#### Android Client

职责：登录、首页、聊天、报告、健康档案、上传等 UI 与状态管理，仅消费后端稳定 API。

#### QA

职责：接口测试、回归测试、医疗安全验证、数据保护验证、发布阻断。

## 6. API / 数据模型

### 6.1 API 原则

- API 版本化：以 `v1` 为稳定面
- 错误统一：返回可机读错误码与可读消息
- 权限分层：Android 只走公开 API；AI Service 仅接受内部契约
- 追踪性：所有链路保留 traceId / requestId

### 6.2 数据原则

- PostgreSQL 继续作为系统主库
- 任何 schema 变更必须新增 migration
- AI 处理结果与审计结果分开存储
- 版本、状态、来源与风险标签必须可追踪

### 6.3 契约方向

- Backend 对 Android：用户、报告、健康档案、聊天、上传、状态查询
- Backend 对 AI Service：内部 ingestion / retrieval / safety / status 查询
- AI Service 不直接面对 Android 客户端

## 7. 技术选择与替代方案

### Backend

- **首选**：维持 Spring Boot 3 + Kotlin/JVM 17，先稳定契约
- **替代**：后续单独提出 Java 21 迁移方案
- **不选**：直接大改语言栈

### AI Service

- **首选**：Python FastAPI + LangGraph
- **替代**：纯 FastAPI 无图编排（能力较弱）
- **不选**：把 AI 逻辑塞回 Android 或 Spring Boot

### Android

- **首选**：继续沿用 Compose + ViewModel + Repository + Hilt
- **替代**：无
- **不选**：直接接 LLM 或绕过 Repository

### 数据层

- **首选**：PostgreSQL + Flyway
- **替代**：未来如需向量能力再评估 pgvector/外部向量库
- **不选**：未经批准切换数据库

## 8. 安全与医疗风险

- **医疗风险**：AI 输出不得表现为确定诊断、用药处方或替代急诊意见；高风险症状必须先分流。
- **隐私风险**：禁止记录 token、健康报告正文、敏感身份信息到日志；对外输出最小化。
- **API 风险**：契约一旦变更，Android 与 AI 都会受影响，必须通过版本化控制。
- **数据库风险**：schema 变更不可直接修改旧 migration，必须新增 migration 并考虑回滚/兼容。
- **安全风险**：AI Service 内部接口必须有受控访问边界，不允许任意客户端直连。

## 9. 迁移与发布计划

### Phase 1：架构与契约冻结

- 输出总体架构
- 冻结 Backend / AI / Android 的职责边界
- 定义 QA 门禁

### Phase 2：Backend Foundation

- 稳定 API v1
- 收敛认证、会话、数据模型与错误模型
- 补测试

### Phase 3：AI Capability Layer

- 建立 AI Service 骨架
- 接入 RAG
- 再引入 Agent
- 完成安全与评估

### Phase 4：Android Iteration

- 按契约更新客户端数据层和 UI
- 对齐错误、加载和权限状态

### Phase 5：Quality and Governance

- 建立回归、审查、风险验证和发布门禁

## 10. 影响

- 影响协作方式：从“各自开发”转为“契约驱动协作”
- 影响文档：需要持续维护 `.ai/` 与 `docs/`
- 影响实施顺序：Backend 和 AI 契约先于 Android 具体适配
- 影响测试：QA 需要在每个阶段介入，而不是最后补测

## 11. 风险

- 前期设计成本增加
- 契约变更会影响多个端，若不冻结边界会返工
- AI 安全边界如果定义不清，会引入医疗与隐私风险
- 若一次推进过多子系统，联调成本会失控

## 12. 验收标准

- 能画出端到端数据流和职责边界
- 每个角色都有明确输入、输出、测试、报告
- 每个阶段都能独立验收
- API、数据库、AI、安全相关风险被显式说明
- 不引入无关依赖，不修改无关模块

## 13. 待确认事项

- Backend Foundation 是否先只冻结 API 契约，还是同步补一部分最小实现
- AI Service 首阶段是否只做骨架与安全边界，还是同时纳入 RAG
- Android 是否需要优先适配登录与首页，还是先对齐聊天与报告链路
- QA 是否以接口级测试为主，还是同步加入端到端回归
