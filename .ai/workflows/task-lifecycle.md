---
name: task-lifecycle
description: Task 生命周期状态机和流转规则
metadata:
  type: harness-workflow
---

# Task Lifecycle

## State Machine

```text
                    BACKLOG
                       │
                  ┌────▼────┐
                  │  READY   │ ◄── Architect 批准 + 依赖满足
                  └────┬────┘
                       │
                  ┌────▼──────┐
                  │ IN_PROGRESS│ ◄── Engineer/Lead 认领
                  └────┬──────┘
                       │
                  ┌────▼────┐
                  │  REVIEW  │ ◄── Lead Review + 自查通过
                  └────┬────┘
                       │
                  ┌────▼────┐
                  │   QA    │ ◄── QA 独立验证
                  └────┬────┘
                       │
                  ┌────▼────┐
                  │ACCEPTED │ ◄── Architect 最终确认
                  └────┬────┘
                       │
                  ┌────▼────┐
                  │  DONE   │ ◄── 归档到 completed-tasks/
                  └─────────┘
```

## Exception States

```text
任何状态 ──→ BLOCKED  ──→ ESCALATED  ──→ Architect 决策
                │                            │
                └── 自救尝试失败后              ├── UNBLOCK (回到原状态)
                                               ├── REWORK (回到 READY)
                                               └── CLOSED (不再推进)
```

## Transition Rules

| From | To | Required |
|---|---|---|
| BACKLOG | READY | Architect 评估依赖满足、范围明确、验收标准定义 |
| READY | IN_PROGRESS | Engineer/Lead 认领、无未满足的 blockedBy |
| IN_PROGRESS | REVIEW | 代码实现完成、本地测试通过、git diff --check 通过 |
| REVIEW | QA | Lead 签字、无 P0 遗留 |
| QA | ACCEPTED | QA PASS + Security Reviewer PASS (如适用) |
| ACCEPTED | DONE | Architect 最终确认、证据归档 |
| ANY | BLOCKED | 明确的阻塞证据 + 自救尝试记录 |
| BLOCKED | READY | 阻塞解除、Architect 确认 |

## Task File Template

Each task in `active-tasks/{TASK-ID}.md`:

```markdown
---
taskId: {TASK-ID}
title: {title}
owner: {role}
domain: backend | ai | android | cross-cutting
status: READY | IN_PROGRESS | REVIEW | QA | ACCEPTED
dependencies: [{taskId}]
blocks: [{taskId}]
createdAt: {ISO date}
---

# {TASK-ID}: {title}

## Goal
{one sentence}

## Input
{what is needed to start}

## Output
{expected deliverables}

## Acceptance Criteria
- [ ] {criterion 1}
- [ ] {criterion 2}

## Allowed Files / Modules
{explicit file list or glob}

## Evidence
{test commands and results}

## Risks
{known risks}

## Blockers
{current blockers if any}

## Agent Receipts
{linked receipts from Engineers/QA/Security}
```
