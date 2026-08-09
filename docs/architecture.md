# Architecture

Doctor-Wang 当前由 Android 客户端、Spring Boot 过渡后端、部署资产和 AI 相关占位目录构成。AI Harness 的作用是把这些模块纳入统一协作流程，而不是立刻重写业务实现。

## Principles

- 先分析，再设计，再实现，最后测试。
- 只改任务范围内文件。
- 关键决策必须可追踪。
- 医疗安全、隐私和审计优先。

## Collaboration model

- Architect 负责方向与评审。
- Lead 负责拆任务和把控模块边界。
- Engineer 负责实现。
- QA 负责验证与回归。

## Scope

本 Harness 只定义协作方式、文档结构和交付门禁，不改变现有产品功能。