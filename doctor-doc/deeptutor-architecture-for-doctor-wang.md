# DeepTutor 架构梳理 & Doctor-Wang 借鉴建议

> 目标：先完整梳理 DeepTutor 的整体架构、分层方式、运行方式与设计模式，再结合 Doctor-Wang 现有形态，提炼出可以直接借鉴的工程原则、模块划分和演进路径。

---

## 1. DeepTutor 项目定位

DeepTutor 是一个 **agent-native learning workspace**，把聊天、研究、答题、可视化、记忆、知识库、伙伴、书籍、作业/学习路径等能力统一到一个核心运行时中。它不是简单的“聊天应用”，而是一个围绕“学习/协作/知识沉淀”的多能力平台。

它的核心特征是：

- **单一运行时，多种能力入口**
- **前端 / 后端 / CLI 共用同一套业务核心**
- **配置、模型、记忆、RAG、工具、伙伴都服务于同一套 agent loop**
- **功能是能力组合，不是烟囱式子系统**

---

## 2. DeepTutor 完整架构总览

### 2.1 顶层结构

DeepTutor 的仓库可以粗略划分为：

- `deeptutor/`：Python 核心包，包含运行时、服务层、agents、tools、capabilities、memory、rag 等
- `deeptutor_cli/`：CLI 入口与命令实现
- `deeptutor/api/`：FastAPI 后端服务
- `web/`：Next.js 前端
- `tests/`：后端、服务、前端相关测试
- `packaging/`、`Dockerfile`、`compose*.yml`：发布与部署
- `assets/`：图片、架构图、版本宣传材料

### 2.2 入口层

DeepTutor 主要有三类入口：

1. **CLI 入口**
   - `deeptutor/__main__.py`
   - `deeptutor_cli/main.py`
   - 用于 `deeptutor chat / kb / memory / start / serve / skill ...`

2. **Web API 入口**
   - `deeptutor/api/main.py`
   - `deeptutor/api/run_server.py`
   - FastAPI + Uvicorn，负责 HTTP、WebSocket、静态资源、运行时生命周期

3. **前端入口**
   - `web/app/layout.tsx`
   - Next.js App Router
   - 负责 UI Shell、路由分区、主题、国际化、布局

### 2.3 核心分层

DeepTutor 的核心分层很清晰：

- **UI 层**：`web/`
- **API 层**：`deeptutor/api/`
- **运行时层**：`deeptutor/runtime/`
- **业务服务层**：`deeptutor/services/`
- **Agent/能力层**：`deeptutor/agents/`、`deeptutor/capabilities/`、`deeptutor/tools/`、`deeptutor/skills/`
- **内容/知识层**：`deeptutor/services/rag/`、`deeptutor/services/memory/`、`deeptutor/book/`、`deeptutor/co_writer/`

---

## 3. DeepTutor 关键运行链路

### 3.1 启动链路

典型启动方式：

```bash
deeptutor start
```

启动链路大致为：

```text
CLI / 启动脚本
  → runtime launcher
  → backend (FastAPI/Uvicorn)
  → frontend (Next.js)
  → workspace settings / runtime home
```

关键点：

- 后端会先设置 runtime home
- 再加载配置文件
- 再启动事件总线、cron、partners、MCP 连接等后台组件
- 前端通过 middleware 将 `/api/*` 和 `/ws/*` 转发给后端

### 3.2 请求链路

以 Chat 为例，基本链路是：

```text
用户输入
  → 前端 Chat 页面
  → 后端 API / WebSocket
  → DeepTutorApp / Agent Loop
  → tools / rag / memory / notebook / subagent / mcp
  → 模型返回
  → 流式输出到前端
```

这说明 DeepTutor 的核心不是“页面”，而是 **统一的 agent 执行循环**。

### 3.3 数据链路

DeepTutor 的数据和状态分得很清楚：

- `services/session/`：会话态
- `services/memory/`：长期/分层记忆
- `services/rag/`：知识库索引与检索
- `services/storage/`：文件与持久化存储
- `services/config/`：运行配置
- `services/partners/`：伙伴 workspace 与会话
- `data/user/settings/`：实际运行配置落盘目录

---

## 4. DeepTutor 的设计模式

### 4.1 单运行时，多 capability

这是它最重要的模式。

- Chat、Quiz、Research、Visualize、Solve、Mastery Path 并不是各自独立的系统
- 它们共享同一个执行引擎
- 差异体现在 capability 配置、工具集、上下文注入、输出格式

**优点**：
- 复用高
- 维护成本低
- 上下文可以自然流转
- 新能力可以通过组合快速落地

### 4.2 插件化 / 模块化边界

DeepTutor 把很多能力都拆成独立目录：

- `agents/`
- `tools/`
- `capabilities/`
- `skills/`
- `services/*`

这是一种典型的“核心稳定、外围可扩展”架构。

### 4.3 配置外置

DeepTutor 把端口、CORS、模型、embedding、auth、UI 偏好、RAG 选项都放到运行时配置里，而不是写死在代码中。

### 4.4 运行时与配置分离

它会区分：

- **代码层**：功能实现
- **runtime 层**：启动、生命周期、环境切换
- **settings 层**：用户可调参数

这样有利于本地/容器/生产统一。

### 4.5 入口统一

CLI、Web、API 都调用同一套业务核心，避免多端逻辑漂移。

### 4.6 RAG / Memory / Session 分离

它没有把“检索”“记忆”“会话”揉成一个大对象，而是拆开管理：

- RAG = 外部知识获取
- Memory = 长期偏好/历史沉淀
- Session = 当前对话上下文

这对复杂系统尤其重要。

---

## 5. DeepTutor 的前端架构特点

### 5.1 Next.js App Router 分区明确

`web/app/` 下分为：

- `(auth)`：登录注册
- `(workspace)`：主工作区
- `(utility)`：辅助功能页
- `(admin)`：后台管理

这种分组方式让页面职责很清晰。

### 5.2 统一布局与全局状态

`web/app/layout.tsx` 做了：

- 字体注入
- ThemeScript
- AppShellProvider
- i18n bridge
- toast viewport

说明它的前端把“壳”和“状态注入”统一到了 root layout。

### 5.3 前端只是壳

复杂逻辑并不放在前端里，前端更多是：

- 展示状态
- 触发操作
- 处理局部交互
- 显示流式结果

---

## 6. DeepTutor 的后端服务分层

### 6.1 `services/` 是核心业务中枢

这个目录非常值得参考，里面有：

- `llm/`：模型选择、provider、执行器、错误映射
- `rag/`：知识库、索引、检索、版本控制
- `memory/`：记忆模型、trace、文档化
- `session/`：会话状态
- `partners/`：伙伴工作区与通信
- `mcp/`：MCP 服务连接与管理
- `sandbox/`：代码执行沙箱
- `config/`：运行配置加载与验证
- `prompt/`：提示词管理
- `notebook/`：笔记/记录服务

### 6.2 生命周期管理

`deeptutor/api/main.py` 会在启动时：

- 校验配置一致性
- 初始化 LLM client
- 启动事件总线
- 自动启动 partners
- 启动 cron
- 进行 PocketBase 可用性检查
- 做 memory 迁移

关闭时会：

- 停 cron
- 停 partners
- 关闭 MCP 连接
- 释放 LLM client

这是一种很成熟的“集中式生命周期治理”。

---

## 7. DeepTutor 的知识、记忆与扩展能力

### 7.1 RAG

DeepTutor 的 RAG 不是单点实现，而是多引擎支持：

- LlamaIndex
- GraphRAG
- LightRAG
- PageIndex
- 其它索引/适配器

### 7.2 Memory

记忆按层组织，便于：

- 可解释
- 可编辑
- 可追溯
- 可迁移

### 7.3 Partners / Subagents

它支持把“伙伴”作为长期协作体，也支持把外部 CLI / Agent 引入当前对话。

### 7.4 Skills / Tools

- Skills 负责“做什么”
- Tools 负责“怎么做”
- Capabilities 负责“这次用哪些工具/上下文”

这个边界很适合大模型应用。

---

## 8. DeepTutor 对 Doctor-Wang 最值得借鉴的点

下面是结合 Doctor-Wang 现状后，最值得吸收的部分。

### 8.1 把“健康助手”统一成一个核心运行时

Doctor-Wang 目前更像：

- Android 客户端
- Spring Boot mock backend
- AI service

建议借鉴 DeepTutor 的思路，把它们收敛成：

- 一个 **核心业务 runtime**
- 多个能力入口：问诊、报告解析、健康档案、随访提醒、风险提示

不要让每个页面各自定义一套业务逻辑。

### 8.2 把功能从页面里抽出来，做成 capability

建议把 Doctor-Wang 的功能拆成：

- `consultation`：AI 问诊
- `report_analysis`：报告解析
- `health_profile`：健康档案
- `follow_up`：随访/提醒
- `document_ingest`：上传与解析
- `triage`：基础分诊与风险提示

这样前端只负责入口，业务逻辑都在能力层。

### 8.3 后端服务层按职责拆分

可以参考 DeepTutor 的 `services/`：

- `llm/`：模型调用与 provider
- `patient/`：用户健康画像
- `documents/`：报告解析与结构化
- `history/`：对话和病历历史
- `storage/`：文件与对象存储
- `audit/`：审计和留痕
- `config/`：环境与运行配置

### 8.4 会话、档案、文件、记忆分开

Doctor-Wang 很容易出现一个常见问题：把所有数据都塞到“用户信息”里。

应该拆成：

- `session`：一次对话
- `profile`：用户基础资料
- `record`：健康档案
- `document`：上传文件
- `memory`：长期偏好 / 历史总结

这能显著减少后期耦合。

### 8.5 前后端边界更清楚

建议前端保持薄：

- 展示
- 输入
- 流式输出
- 状态同步

把以下内容放后端：

- 报告解析编排
- LLM 路由
- 风险分级
- 数据持久化
- 审计日志

### 8.6 配置中心化

DeepTutor 把运行参数集中管理，这对 Doctor-Wang 也很重要：

- API base url
- 模型 provider
- 报告解析策略
- 脱敏规则
- 上传限制
- 环境开关
- 风险策略阈值

### 8.7 启动/关闭生命周期统一治理

Doctor-Wang 未来如果加：

- 后台任务
- 定时健康提醒
- 文件处理队列
- AI 分析任务

就应该有统一的 bootstrap/teardown，而不是散落在多个 controller 里。

### 8.8 可观察性与审计

DeepTutor 很强调“可追踪”。Doctor-Wang 更需要这一点，因为它涉及健康信息。

建议至少做到：

- 每次解析有 trace id
- 每次模型调用有 request id
- 每次风险提示可回溯输入来源
- 每次报告生成可重放

---

## 9. Doctor-Wang 当前架构与 DeepTutor 的对照映射

### 9.1 你现在的项目结构

根据当前仓库，Doctor-Wang 主要由：

- `app/`：Android 客户端
- `doctor-wang-mock-server/`：Spring Boot 后端
- `ai-service/`：Python AI 服务
- `deploy/`：部署资产
- `docs/`：架构与决策文档

### 9.2 可对标的思路

| DeepTutor | Doctor-Wang 可借鉴方向 |
|---|---|
| `runtime/` | 统一启动器 / 生命周期管理 |
| `services/llm` | AI Provider 抽象层 |
| `services/rag` | 报告/病历检索与知识增强层 |
| `services/memory` | 用户长期健康记忆层 |
| `agents/` + `capabilities/` | 问诊/报告/随访/档案能力拆分 |
| `web/` 薄前端 | Android 也保持 UI 薄，逻辑上移 |
| 配置外置 | base url、模型、风险阈值、脱敏策略配置化 |
| 生命周期管理 | 后台任务、同步任务、AI 请求统一治理 |

---

## 10. 推荐给 Doctor-Wang 的下一步演进方案

### 第一阶段：先把边界理顺

- 建立统一的 AI 服务接口层
- 将问诊、报告解析、档案查询抽成独立能力
- 明确 session/profile/document/record/memory 的边界

### 第二阶段：补齐 runtime 思维

- 增加统一初始化流程
- 配置集中化
- 后台任务统一启动/停止

### 第三阶段：建立可扩展能力体系

- 新增 capability registry
- 新增 tool/provider 抽象
- 支持不同模型/解析引擎切换

### 第四阶段：加强审计与可观测

- trace id
- request log
- AI 调用记录
- 文件解析链路追踪

---

## 11. 结论

DeepTutor 最值得 Doctor-Wang 学习的，不是某个具体页面，而是它的 **系统化分层、统一 runtime、能力插件化、配置外置、会话/记忆/RAG 分离** 这套方法论。

如果 Doctor-Wang 继续扩展健康问诊、报告解析、健康档案、随访提醒等能力，最稳妥的路线就是：

> **把“健康助手”从页面集合，升级成“统一运行时 + 可组合能力”的系统。**

---

## 12. 参考文件

### DeepTutor
- `README.md`
- `pyproject.toml`
- `deeptutor/api/main.py`
- `deeptutor/api/run_server.py`
- `deeptutor_cli/main.py`
- `web/app/layout.tsx`
- `web/app/(workspace)/`
- `web/app/(utility)/`

### Doctor-Wang
- `README.md`
- `docs/architecture.md`
- `doctor-wang-mock-server/README.md`
- `ai-service/README.md`
- `deploy/README.md`
