---
name: agent-handoff
description: Agent 之间的标准交接格式
metadata:
  type: harness-workflow
---

# Agent Handoff Contract

每次交接必须包含：

```markdown
## Handoff
- Task ID:
- Agent:
- Status: IN_PROGRESS / READY_FOR_REVIEW / BLOCKED / VERIFIED
- Scope changed:
- Files changed:
- Decisions:
- Tests and exact commands:
- Results and evidence:
- Risks / security / medical concerns:
- Open questions:
- Next owner:
```

禁止只发送“已完成”“测试通过”等无法复现的结论。任何跳过的检查都必须写出原因和后续责任人。
