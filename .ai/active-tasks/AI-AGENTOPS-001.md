---
taskId: AI-AGENTOPS-001
title: AgentOps 最小可用集成
owner: Architect
domain: cross-cutting
status: ACCEPTED
dependencies: [RAG-001]
blocks: []
createdAt: 2026-10-15
---

# AI-AGENTOPS-001: AgentOps 最小可用集成

## Goal

在不改变 Doctor-Wang 外部 API 和医疗安全边界的前提下，将 AgentOps-J 作为可选 LLM 网关接入并可本地展示。

## Input

- `/Users/a1234/code/agentops-j` 的 OpenAI-compatible chat gateway。
- `ai-service` 现有 OpenAI-compatible generation provider。

## Output

- AgentOps 安全兼容改造。
- Doctor-Wang Compose override、配置样例和使用文档。
- 自动化测试和本地 smoke 证据。

## Acceptance Criteria

- [x] system/user message 和生成参数保真转发。
- [x] 生产模式禁用模拟 provider 并 fail closed。
- [x] Doctor-Wang 可选启用 AgentOps 且可一键回滚直连。
- [x] 两仓测试和本地 smoke 通过。
- [x] 两仓独立提交并推送。

## Allowed Files / Modules

- `agentops-j/src/main/**`, `agentops-j/src/test/**`, `agentops-j/README.md`, `agentops-j/.env.example`
- `doctor-wang/deploy/**`, `doctor-wang/docs/**`
- `doctor-wang/.ai/**`, `doctor-wang/.claude/plan/**`

## Risks

- AgentOps 默认演示 provider 不能进入医疗回答链路；集成配置必须显式关闭。
- AgentOps 只允许容器内网访问；本切片不增加公网入口。
- 本地展示使用脱敏固定响应，不使用真实患者数据或生产密钥。

## Blockers

None.

## Agent Receipts

`.ai/handoffs/AI-AGENTOPS-001/architect.md`.
