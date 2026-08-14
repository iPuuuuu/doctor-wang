---
name: decisions
description: AI Harness 与项目的关键技术决策记录
metadata:
  type: project
  updated: 2026-08-12
---

# Decisions

## Current Entries

| ID | Title | Status | Date |
|---|---|---|---|
| ADR-0001 | Multi-Agent Development Harness | proposed | 2026-08-09 |
| ADR-0002 | JWT Authentication & Credential Migration | accepted | 2026-08-09 |
| ADR-0003 | Authentication Release Hardening | accepted | 2026-08-10 |
| ADR-0004 | Harness v2 — State Directory, Task Lifecycle, Agent Receipts | proposed | 2026-08-12 |

## ADR-0004: Harness v2 Upgrade

**Decision:** Restructure `.ai/` with `state/` subdirectory, proper task lifecycle state machine, Agent Receipt template, escalation workflow, and deduplicated role files.

**Rationale:** The original harness (ADR-0001) established the role registry and workflow documents but lacked: structured state tracking, formal task lifecycle, escalation path, and mandatory Agent Receipts.

**Changes:**
- `.ai/state/` — canonical location for project-state, task-board, active-tasks, completed-tasks
- `.ai/workflows/task-lifecycle.md` — BACKLOG→READY→IN_PROGRESS→REVIEW→QA→ACCEPTED→DONE with BLOCKED/ESCALATED exception states
- `.ai/workflows/agent-receipt.md` — mandatory structured delivery report
- `.ai/workflows/qa.md` — independent QA verification checklist
- `.ai/workflows/escalation.md` — self-recovery-first escalation protocol
- Role files consolidated to `-agent.md` canonical versions

**No business code, database, or deployment changes.**
