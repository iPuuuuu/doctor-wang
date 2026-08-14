---
name: doctor-wang-agent-roles
description: Doctor-Wang 多 Agent 角色注册表
metadata:
  type: harness
---

# Agent Role Registry

## Roles

| 角色 | 文件 | 权责边界 | 主要交付物 |
|---|---|---|---|
| Architect | `architect-agent.md` | 系统边界、技术决策、风险和验收标准 | 架构决策与实施计划 |
| Backend Lead | `backend-lead-agent.md` | 后端任务拆分和设计评审 | 后端任务包、评审结论 |
| Backend Engineer | `backend-engineer-agent.md` | Spring Boot、API、数据库和后端测试实现 | 代码、migration、测试和契约 |
| Android Lead | `android-lead-agent.md` | Android 方案、任务拆分和体验一致性 | Android 任务包、评审结论 |
| Android Engineer | `android-engineer-agent.md` | Compose、ViewModel、Repository 和客户端测试实现 | 客户端代码和测试 |
| AI Lead | `ai-lead-agent.md` | Agent、RAG、Prompt、模型和 Evaluation 设计 | AI 方案、评估门禁 |
| AI Engineer | `ai-engineer-agent.md` | AI pipeline、tool、检索和安全策略实现 | AI 代码、测试和可观测性 |
| QA Engineer | `qa-engineer-agent.md` | 测试矩阵、回归、质量和发布判定 | 测试计划、证据、缺陷报告 |
| Security Reviewer | `security-reviewer-agent.md` | 威胁建模、隐私、认证授权和安全发布审查 | 安全审查、阻断项和复核结论 |

## Selection Rules

1. Architect first — judges impact scope and delegates to domain Lead
2. Lead decomposes — splits into independently verifiable tasks
3. Engineer implements — only within approved scope
4. QA and Security Reviewer — independent verification with blocking power
5. Architect — final ACCEPT / REWORK / BLOCK decision

All roles must obey root `AGENTS.md`. Never describe mock/fake/demo capabilities as production-ready.
