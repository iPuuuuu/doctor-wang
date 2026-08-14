---
name: escalation
description: 阻塞上报与决策流程
metadata:
  type: harness-workflow
---

# Escalation Workflow

## When to Escalate

Only escalate AFTER attempting self-recovery. The Engineer/Lead must first:

1. Read existing code and docs
2. Search for existing implementations
3. Check tests and ADRs
4. Attempt to shrink task scope
5. Attempt to split into smaller tasks
6. Check for existing project patterns

## Self-Recovery Checklist

Before escalating, document:

```markdown
## Self-Recovery Attempt

- [ ] Read relevant source files: {files}
- [ ] Searched for existing patterns: {query}
- [ ] Checked tests: {test files}
- [ ] Attempted scope reduction: {approach}
- [ ] Attempted task split: {sub-tasks}
- [ ] Identified exact blocker: {description}
```

## Escalation Format

When self-recovery fails, submit to Architect:

```markdown
## Escalation

Task ID: {TASK-ID}
Agent: {role}
Date: {date}

### Blocker
{concise description of what is blocking progress}

### Evidence
{logs, error messages, test output, file references}

### Attempted Recovery
{what was tried, in order}

### Why Recovery Failed
{root cause analysis}

### Options Presented
1. {option A — with pros/cons}
2. {option B — with pros/cons}

### Recommended Decision
{which option and why}

### Required From Architect
{exactly what decision or authorization is needed}
```

## Architect Response

```markdown
## Escalation Decision

Task ID: {TASK-ID}
Decision: UNBLOCK | REWORK | CLOSED
Rationale: {why}
New Scope (if changed): {updated scope}
Next Owner: {role}
Next Action: {concrete next step}
```

## Valid Escalation Reasons

- Product direction choice (not technical)
- Irreversible architecture decision
- Explicit permission for external resource access
- Critical business decision with compliance/legal implications
- Cross-cutting concern affecting multiple domains

## Invalid Escalation Reasons

- "I don't know how to do this" (→ read docs, search, try)
- "The test is failing" (→ debug, read the error)
- "I need more context" (→ read the existing code)
- "This is too hard" (→ split into smaller tasks)
