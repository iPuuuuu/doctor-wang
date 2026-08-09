# Feature Workflow: Doctor-Wang 功能开发流程

适用于任何新功能、API 变更、AI Agent、报告处理、数据库变更和 Android 页面变更。

## Phase 0 — 角色与范围

读取：`.ai/context.md`、相关 role 文件和现有文档。确认用户授权的目录、非目标和验收标准。

## Phase 1 — Architecture first

由 Architect 输出：

- 当前实现和缺口
- 数据流和模块边界
- API / 数据库设计
- 安全、隐私、医疗风险
- 替代方案和取舍
- migration、回滚和发布计划

在用户确认前不得编码。

## Phase 2 — Backend / AI / Android design

按领域分别输出实现计划：

- Backend：Controller、Service、Repository、migration、API test
- AI：Graph、state、prompt、tool、RAG、safety、evaluation
- Android：Screen、ViewModel、Repository、缓存、错误状态、UI test
- QA：测试矩阵、异常场景、性能和回归门禁

## Phase 3 — Incremental implementation

只修改批准范围内的文件。每完成一个垂直切片就运行对应测试，不等待所有代码完成后才首次验证。

## Phase 4 — Required artifacts

每项功能必须同步更新：

- 测试
- API/OpenAPI
- 数据库 migration（如有）
- 用户/开发文档
- ADR 或设计文档（如改变架构）

## Phase 5 — Delivery gate

交付前报告：修改文件、测试命令和结果、兼容性、数据迁移、风险、未完成事项。任何失败测试、安全风险或医疗安全问题都必须明确列出，不得宣称完成。
