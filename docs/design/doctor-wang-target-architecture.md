# Doctor-Wang 目标架构设计文档

> 状态：Proposed
> 日期：2026-08-08
> 角色：Architect Agent
> 参考原则：借鉴 DeepTutor 的系统化分层、统一 runtime 思维、能力模块化、配置外置、记忆/知识分离；不照搬其具体技术栈与目录实现。

## Architecture Decision

### 背景

Doctor-Wang 当前已经具备三类核心资产：

1. **Android 客户端**：包含登录、首页、聊天、健康档案、报告上传、报告详情等页面与基础状态管理。
2. **Spring Boot mock server**：已经不只是“纯 mock”，而是实际承载了认证、会话、报告、健康档案、任务轮询、文件存储适配、AI 调用适配等职责。
3. **AI service**：仓库中已经存在 `ai-service/`，包含 FastAPI、LangGraph 风格的处理管线、RAG、vector store、safety 等雏形能力，但整体边界、运行角色和与 Spring Boot 的职责分工尚未完全稳定。

项目的下一阶段不是“继续在现有代码上叠功能”，而是要把 Doctor-Wang 从“演示链路 + 渐增代码”演进为一个可长期扩展的 **健康辅助 AI 平台**。

这个平台必须同时满足：

- Android 体验可持续演进
- 后端边界清晰，避免继续膨胀成 God Service
- AI service 能独立演进，但不能越过权限与审计边界
- 多 agent 协作未来可扩展，但不能为了 agent 而 agent
- 会话、档案、报告、文件、记忆边界明确
- 配置、可观测性、安全与隐私从一开始就是架构一部分

### 当前架构与约束

#### 当前实际形态

**Android：**
- Kotlin + Jetpack Compose + ViewModel + Repository + Hilt
- 已有本地数据库、SessionManager、网络层、多个 screen 与 viewmodel
- 当前 repository 与 viewmodel 边界仍偏“页面驱动”，域边界不够清晰

**后端：**
- `doctor-wang-mock-server` 基于 Spring Boot 3 + Kotlin/JVM 17 + PostgreSQL + Flyway
- 代码中已经存在：`AuthController`、`ReportController`、`ReportV1Controller`、`BackendService`、`ReportProcessingService`、`AiServiceClient`、`FileStorageService`、`ReportJobStateMachine`、`PostgreSqlPollingWorker`
- 说明后端已经同时承担了 API 层、业务层、任务编排层、文件层、AI gateway 层

**AI service：**
- 已有 `config.py`、`graph.py`、`jobs.py`、`parsing.py`、`rag.py`、`safety.py`、`vectorstore.py`、`providers.py`
- 说明已经具备“能力层雏形”，但还没有被严格定义为独立的智能能力边界

#### 当前主要问题

1. **后端边界膨胀**
   - Spring Boot 当前既像业务后端，又像异步任务平台，还像 AI 编排入口。
   - 如果继续在 `BackendService` 一类对象中加逻辑，会快速演变成难以维护的 God Service。

2. **Android 仍偏页面导向，不是域导向**
   - 当前 screen / viewmodel / repository 结构能工作，但不利于未来把 chat、report、profile、memory 独立演进。
   - 报告状态、聊天状态、用户状态、会话状态之间容易出现共享模型或隐式耦合。

3. **AI service 的职责边界不够硬**
   - 代码中已有 parsing / rag / safety / vectorstore，但尚未明确“AI service 只负责智能能力，不负责权限与业务事实”。
   - 如果不收紧边界，未来容易出现“AI service 变成第二业务后端”的问题。

4. **领域对象边界仍不够清楚**
   - report、file、session、memory、profile 有天然不同的数据生命周期与权限语义。
   - 当前实现已经开始分化，但在架构层还缺少统一定义。

5. **配置、可观测性、策略版本尚未上升为一等公民**
   - 模型、prompt、阈值、重试策略、OCR/解析能力、风险策略如果继续散落在代码中，会导致后续不可治理。

6. **多 agent 协作缺少长期稳定的运行边界**
   - 如果没有统一 runtime 与 capability 边界，未来 agent 只会变成“多个 prompt 函数”，可维护性会很差。

#### 必须遵守的约束

- Android 不得直连模型供应商。
- Spring Boot 仍然是权限、用户归属、业务事实、审计链的唯一权威来源。
- AI service 不能替代业务后端做授权判断。
- 数据库继续以 PostgreSQL + Flyway 为主，不随意换库。
- 当前后端事实基线仍是 Kotlin/JVM 17；Java 21 迁移必须单独立项。
#### 部署与环境基线

- 当前私有部署信息显示目标环境为阿里云 ECS + Docker 容器。
- 现有部署文档里出现过 MySQL / RDS 设想，但仓库主线技术基线与当前实现均以 PostgreSQL 为准。
- 因此本设计把 **PostgreSQL** 作为正式数据库基线；MySQL 仅视为未定的部署想法，不作为本设计实施依据。
- 生产环境在真实用户使用前必须具备 HTTPS、受控访问与审计能力；没有 HTTPS 时只能做内部演示或沙箱验证。


本次目标不是给出“一套立即全部落地的重构清单”，而是定义一个未来 6–12 个月可持续演进的目标架构。

#### 核心目标

1. 明确 **Android / Spring Boot / AI service** 三层边界。
2. 明确 **session / profile / report / file / memory** 五个核心领域边界。
3. 让 Doctor-Wang 从“页面 + 接口 + AI 调用”演进为“**契约驱动、状态显式、能力模块化**”的平台架构。
4. 为未来多 agent 协作预留清晰的 runtime 与 capability 边界。
5. 在不照搬 DeepTutor 的前提下，借鉴其系统化分层和能力组织方式。

#### 非目标

- 不在本设计中直接要求拆成大量微服务。
- 不在本设计中要求立刻迁移到 Java 21。
- 不在本设计中规定唯一模型供应商或唯一向量数据库实现。
- 不把 Doctor-Wang 设计成通用 agent 平台或通用学习平台。

### 方案

推荐采用 **契约优先的三层平台化架构**。

```text
Android Client
  ├─ UI / Navigation / ViewModel
  ├─ Domain-facing Repository
  ├─ Local cache / session snapshot
  └─ Upload / chat / profile / report entrypoints

        ↓ only stable public API

Spring Boot API / Orchestration Layer
  ├─ Auth & Session
  ├─ User / Profile domain
  ├─ Report / File domain
  ├─ Conversation / Memory metadata domain
  ├─ Audit / Policy / Trace
  ├─ Job orchestration
  └─ Internal AI client

        ↓ internal service contract only

AI Service (FastAPI + LangGraph)
  ├─ Parsing / OCR / extraction
  ├─ Chunking / embedding / vector operations
  ├─ Retrieval / context assembly
  ├─ Agent workflows
  ├─ Safety review / evaluation
  └─ Capability runtime
```

#### 方案核心思想

1. **Android 是体验层，不是业务权威层。**
   Android 负责用户体验、状态表达、弱缓存、交互节奏，不负责决定权限、报告所有权、AI 策略和长期记忆的最终事实。

2. **Spring Boot 是系统事实层。**
   它必须掌握用户身份、资源归属、状态机、审计事件、对 AI 的授权入口和对外契约稳定性。

3. **AI service 是智能能力层。**
   它应专注于解析、检索、生成、安全评估与 agent runtime，不应掌握业务系统的最终控制权。

4. **域边界先于服务拆分。**
   是否未来拆成更多服务，不应先决定；应先把域边界、接口边界、状态边界做清楚。

5. **runtime 和 capability 要显式化。**
   借鉴 DeepTutor 最有价值的不是目录结构，而是“能力不是散落函数，而是挂在统一运行时和明确能力接口上的模块”。

### 模块拆分

## 1. Android 客户端架构

建议 Android 未来演进为四层：

```text
ui/
presentation/
domain/
data/
```

#### 推荐职责

- `ui/`：Compose 页面、组件、导航、视觉状态
- `presentation/`：ViewModel、UiState、UiEvent、screen-specific mapping
- `domain/`：按领域拆分 use case / repository interface / domain model
- `data/`：Retrofit、Room、DataStore、DTO mapper、repository implementation

#### 重点不是“换目录”，而是换边界

Android 端最重要的不是机械重命名，而是把当前能力拆成独立域：

- `chat`
- `report`
- `upload`
- `profile`
- `session`
- `settings`

#### Android 目标原则

1. **每个 ViewModel 只拥有本域状态**
   - ChatViewModel 不持有 report processing state
   - UploadViewModel 不关心聊天消息流
   - ReportDetailViewModel 不直接依赖聊天 UI model

2. **Repository 按域拆分，不再走“大而全接口”**
   推荐目标接口：
   - `ChatRepository`
   - `ReportRepository`
   - `ProfileRepository`
   - `SessionRepository`
   - `MemoryRepository`（未来需要时再引入）

3. **本地缓存必须带用户隔离**
   尤其 report、chat snapshot、profile summary 不能继续以“单用户 demo”思维缓存。

4. **状态必须显式化**
   例如上传与报告处理不应该只用 `isLoading` 一类布尔值，而应是：

```text
idle → uploading → processing → completed
                       ├→ failed
                       └→ review_required
```

#### Android 结论

Doctor-Wang Android 不需要照搬 DeepTutor 的前端结构，但要借鉴它的思想：
**UI 不是能力本身，能力必须有自己的状态和边界。**

## 2. Spring Boot 后端架构

Spring Boot 应重新定义为 **业务编排层 / BFF / 系统权威层**。

建议按领域与职责拆成以下模块：

```text
auth/
session/
profile/
report/
file/
conversation/
memory/
audit/
job/
ai/
shared/
```

#### 推荐边界

- `auth/`：登录、token/session、认证上下文
- `session/`：用户对话会话元数据与上下文边界
- `profile/`：健康档案、用户长期稳定事实
- `report/`：报告主记录、结构化结果、状态机
- `file/`：对象存储引用、上传校验、派生文件元数据
- `conversation/`：消息流、会话轮次、问答上下文边界
- `memory/`：长期低风险记忆元数据与启用策略
- `job/`：异步处理任务、重试、补偿、轮询/派发
- `ai/`：内部 AI client，不承载业务权限逻辑
- `audit/`：审计事件、追踪、策略版本、风险记录

#### 后端的核心角色

Spring Boot 必须同时承担四个角色：

1. **对外 API 契约提供者**
2. **用户归属与权限守门人**
3. **业务状态机和资源生命周期管理者**
4. **对 AI service 的受控调用方**

#### 明确不应做的事

- 不把复杂 prompt 编排和 retrieval 逻辑继续塞回 Spring Boot
- 不让 Controller 直接做业务逻辑
- 不让 AI client 反向决定资源访问策略
- 不让 `BackendService` 持续承担越来越多不相干职责

#### 后端的目标结构不是微服务，而是“模块化单体优先”

未来 6–12 个月内，Doctor-Wang 更适合：
- **先做模块化单体**
- 对 AI 保持独立服务边界
- 对 object storage / queue / vectorstore 使用适配器边界

这样既能保持演进空间，也不会过早引入大规模分布式复杂度。

## 3. AI service 架构

AI service 的目标定位应当是：

> 一个受控的智能能力运行时，而不是第二个业务系统。

#### 建议内部能力模块

```text
config/
schemas/
runtime/
capabilities/
parsing/
retrieval/
memory/
agent/
safety/
evaluation/
integrations/
```

#### 能力视角拆分

1. **Parsing Capability**
   - 文件解析
   - OCR / 文本抽取
   - 结构化字段提取

2. **Retrieval Capability**
   - report retrieval
   - knowledge retrieval
   - future memory retrieval
   - context assembly

3. **Generation Capability**
   - 摘要
   - 问答
   - 解释
   - 风险提示

4. **Safety Capability**
   - 高风险内容识别
   - 不确定性注入
   - response gating
   - 人工复核升级条件

5. **Evaluation Capability**
   - prompt / model / parser 版本评估
   - golden set 回归
   - retrieval quality 检查

#### AI service 的硬边界

AI service **不能**：
- 决定用户是否有权访问 report
- 直接成为客户端公开接口
- 直接持有完整业务真相
- 把“模型输出”当作权威业务状态

AI service **可以**：
- 接收后端签发的内部任务请求
- 处理 report / retrieval / generation pipeline
- 输出带来源、版本、置信度和 safety 标记的结果
- 维护自己的 capability runtime 与配置

### agent 角色拆分方式

Doctor-Wang 不应一开始就设计成“很多 agent 互相对话”的体系，而应该先定义少量、职责稳定的 agent 角色。

推荐的最小角色集：

#### 1. Triage Agent

职责：
- 识别问题类型
- 识别风险等级
- 判断是否需要 report retrieval / profile context / memory context
- 判断是否要进入人工复核或就医分流

#### 2. Report Agent

职责：
- 解释单次报告
- 生成摘要、重点发现、建议
- 保持和 report citations 强绑定

#### 3. Retrieval Agent

职责：
- 从 report / knowledge / future memory 中检索上下文
- 做证据收集与重排
- 不直接生成最终面向用户的答案

#### 4. Conversation Agent

职责：
- 管理普通对话轮次
- 组装上下文
- 调用 retrieval / triage / report capability
- 生成最终用户输出

#### 关键原则

- **agent 是 capability orchestration 单元，不是权限单元**
- **agent 之间通过结构化输入输出交互，而不是隐式共享全局上下文**
- **未来增加 agent 时，也必须先定义 capability contract**

### 会话 / 用户档案 / 报告 / 文件 / 记忆 的边界

这是 Doctor-Wang 长期最重要的建模问题之一。

#### 1. Session

**定义：** 用户与系统一次连续交互的上下文容器。  
**特点：** 短生命周期、可关闭、可总结、可关联多个消息轮次。  
**权威层：** Spring Boot。  
**AI 视角：** 作为上下文输入，但不是长期事实来源。

#### 2. Profile

**定义：** 用户稳定、长期、明确归属的健康档案事实。  
例如：年龄段、性别、既往病史、过敏史、慢病记录、长期用药。  
**权威层：** Spring Boot。  
**AI 视角：** 只读快照输入。

#### 3. Report

**定义：** 某一次上传或导入的具体医疗报告及其解释结果。  
**特点：** 强版本化、强引用、强审计。  
**权威层：** Spring Boot 记录元数据和状态；AI service 产生解释结果。

#### 4. File

**定义：** 原始文件与派生文件对象。  
包括 PDF、JPG、OCR 中间产物、解析文本引用对象等。  
**特点：** 生命周期与 report 关联但不等同于 report。  
**原因：** 文件可以重解析、替换存储、单独清理。

#### 5. Memory

**定义：** 从会话中提炼出的可复用、低风险、可审计的长期偏好或总结。  
例如：用户偏好的解释风格、持续关注的健康目标、对话摘要。  
**不是：** report 原文数据库，也不是 profile 的替代。  
**原则：** memory 必须可更新、可失效、可审计、可禁用。

#### 边界结论

```text
session != memory
profile != report
report != file
memory != report knowledge base
```

这四条是未来架构稳定性的关键。

### API / 数据模型

## API 设计原则

1. **对外 API 以 Spring Boot 为唯一公开入口**
2. **AI service 只暴露 internal API**
3. **所有 AI 结果都必须带状态、来源、版本或风险标记**
4. **旧接口保留兼容窗口，新接口走 `/api/v1/...`**

## 数据模型原则

#### Spring Boot 持有的权威实体

- user
- user_session
- health_profile
- medical_report
- medical_report_file
- report_processing_job
- conversation_thread
- conversation_message
- memory_record（未来）
- audit_event

#### AI service 持有或管理的智能处理实体

- parsing artifact
- chunk
- embedding reference
- retrieval result
- prompt version
- model version
- safety decision
- evaluation run

#### 核心建模原则

- **业务真相存后端**
- **智能中间状态存 AI layer 或派生存储**
- **跨层只通过结构化契约交互**

### 技术选择与替代方案

## 推荐主方案

### Android
- 保持 Kotlin + Compose + ViewModel + Hilt
- 增量引入更清晰的域边界

### Backend
- 保持 Spring Boot 3 + Kotlin/JVM 17
- 做模块化单体治理
- 不立即做 Java 21 迁移

### AI service
- 保持 FastAPI + LangGraph 方向
- 将现有 parsing / rag / safety / jobs 重组为明确 capability runtime

### 数据与存储
- 主数据：PostgreSQL
- 文件：对象存储适配层
- 向量：优先保持适配器设计，不急于锁死单一实现

## 替代方案

### 替代方案 1：继续让后端集中承载大部分逻辑
**不推荐。**
短期省事，但长期会让后端边界失控，AI 能力无法独立演进。

### 替代方案 2：全面微服务化
**不推荐。**
Doctor-Wang 当前体量不足以支撑大规模微服务复杂度，会过早引入部署、观测、事务和联调负担。

### 替代方案 3：Agent runtime 中心化架构
**暂不推荐作为当前目标态。**
这更像未来 AI 平台演进方向，不适合作为当前 6–12 个月最优落地架构。

### 安全与医疗风险

## 1. 权限与资源边界

- 只有 Spring Boot 能判断用户是否能访问 report / file / profile / session
- AI service 不信任客户端 userId
- internal AI request 必须来自后端受控调用

## 2. 隐私与数据最小化

- 报告原文、OCR 结果、敏感字段不进入普通应用日志
- memory 不应无控制地持久化高敏感健康详情
- public knowledge 与 private report retrieval 必须隔离

## 3. 医疗安全

- Conversation Agent 生成的最终文本必须经过 safety capability
- 高风险症状必须触发 triage / 就医分流
- 不输出确定性诊断、处方、停药/改药指令
- 不得因为 retrieval 缺失而编造来源

## 4. 可审计性

至少需要可追踪：
- traceId
- requestId
- sessionId
- reportId
- jobId
- modelVersion
- promptVersion
- parserVersion
- safetyPolicyVersion

### 迁移与发布计划

## Phase 1：架构收边界

目标：把当前代码从“混合职责”整理成明确边界，但不追求功能大迁移。

- 文档冻结目标架构
- 收紧 Android / Backend / AI service 责任定义
- 明确 report / file / memory / session / profile 的边界

## Phase 2：后端模块化治理

- 缩小 `BackendService` 职责
- 明确 `ai client`、`job orchestration`、`report domain`、`file domain`
- 稳定对外 API 和内部 AI contract

## Phase 3：AI runtime 重组

- 将 parsing / rag / safety / jobs 抽成 capability runtime
- 建立 agent 角色边界
- 建立 structured input / output contract

## Phase 4：Android 域化

- repository 按域拆分
- ViewModel 与状态按域隔离
- 报告、聊天、profile、session 的本地状态明确化

## Phase 5：可观测性与策略治理

- 配置外置
- trace 与 audit 统一
- prompt / model / parser / safety policy 版本纳管

### 影响

## 对 Android 的影响

- 架构会从“页面导向”转向“领域导向”
- repository 和 UI state 需要更明确拆分
- 本地缓存会变得更安全、更可维护

## 对 Backend 的影响

- 现有服务对象需要去膨胀
- controller/service/repository/adapter 的分工会更清晰
- API 会更稳定，但前期治理成本会上升

## 对 AI service 的影响

- 不再只是“功能文件集合”，而是变成明确的智能运行时
- safety 和 retrieval 会从附属能力提升为核心能力
- 未来多 agent 扩展会更可控

## 对部署与运维的影响

- 不一定立即增加很多新组件
- 但会要求更统一的配置、日志、追踪、版本与运行策略管理

### 风险

| 风险 | 等级 | 说明 | 缓解措施 |
|---|---|---|---|
| 后端重构过程中边界未真正收紧 | P1 | 名称变了但职责没变 | 以 use-case / domain ownership 为准做 review |
| Android 只是改目录未改依赖方向 | P1 | 结构看起来更好但耦合仍在 | 明确 repository 与 viewmodel 只依赖本域模型 |
| AI service 继续扩展成“第二后端” | P0 | 权限与真相边界混乱 | 强制 internal contract + backend authority |
| memory 与 profile/report 混淆 | P0 | 容易产生错误医疗上下文或隐私泄漏 | 明确不同生命周期与写入规则 |
| 过早引入复杂多 agent 编排 | P1 | 系统复杂度上升过快 | 先固定少量 agent 角色 |
| 配置继续散落在代码中 | P1 | 无法治理模型与策略变化 | 建立配置中心化与版本化规则 |

### 验收标准

#### 架构层

- 能清晰画出 Android / Spring Boot / AI service 的边界图
- 能清晰解释 session / profile / report / file / memory 的不同角色与生命周期
- 能说明为什么 AI service 不能持有权限判断
- 能说明为什么 Doctor-Wang 不是通用 agent 平台

#### 工程层

- Android、Backend、AI service 各自职责与接口清晰
- 后端对外 API、对内 AI contract 可分开治理
- 可观测性字段、策略版本、配置归属有统一定义

#### 演进层

- 能支持未来多 agent 协作
- 不依赖立即拆成大规模微服务
- 不依赖立即迁移 Java 21
- 不依赖单一模型/向量数据库实现

### 待确认事项

1. memory 在 Doctor-Wang 第一阶段是否只存“对话摘要与偏好”，还是允许存部分结构化健康关注点？
2. AI service 的 internal contract 是否准备在下一阶段正式版本化？
3. report / file 删除的补偿清理机制是否进入近期范围？
4. 向量检索是否优先只用于 report，再逐步扩展到 knowledge 与 memory？
5. Android 是否接受按域重构 repository，而不是继续在现有统一 repository 上叠功能？

## 最终结论

Doctor-Wang 最适合的目标架构不是 DeepTutor 的复制品，而是：

> **借鉴 DeepTutor 的方法论，把 Doctor-Wang 建成一个“Android 体验层 + Spring Boot 业务编排层 + AI service 智能能力层”的契约驱动平台。**

其中最关键的四个原则是：

1. **系统真相在后端，不在 AI**
2. **能力模块化，但权限集中化**
3. **状态显式化，而不是隐式共享**
4. **session / profile / report / file / memory 必须严格分域**

如果后续按这个架构演进，Doctor-Wang 才能在未来多 agent 协作、RAG 增强、报告解析增强、配置治理和安全审计方面保持可维护性，而不是继续在当前混合结构上累积复杂度。
