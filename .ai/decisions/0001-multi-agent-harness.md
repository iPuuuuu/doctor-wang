---
id: ADR-0001
title: Establish a governed multi-agent development harness
status: proposed
date: 2026-08-09
owners: [architect-agent]
---

# ADR-0001：建立受治理的多 Agent 开发体系

## 背景

Doctor-Wang 已有 Android、Spring Boot、AI Service 和部署资产，也已有角色与工作流文档，但缺少统一角色注册、交接契约、独立安全审查和可执行的协作边界。

## 决策

采用 Architect → Lead → Engineer → QA/Security Reviewer → Architect 的协作链。Agent 只能在明确的职责和批准范围内工作；权限、医疗安全和发布结论不能由单一 Engineer 或模型隐式决定。

## 取舍

先建立文档契约和质量门禁，再扩展多 Agent runtime。这样会增加交接和审查成本，但能保留可追溯性、用户隔离、医疗安全和回滚能力。

## 影响

- 角色定义集中在 `.ai/roles/`。
- 流程定义集中在 `.ai/workflows/`。
- 架构决策集中在 `.ai/decisions/`。
- 可复用 Agent 技能和检查说明集中在 `.skills/`。
- 本决策不改变任何业务代码、数据库、依赖或部署配置。

## 验收标准

- 九个角色均有职责、输入、输出、禁止事项和工作流程。
- 新任务可以按主流程和 handoff contract 交接。
- 安全审查与 QA 具备独立阻断权。
- 未完成证据不能被标记为通过。
