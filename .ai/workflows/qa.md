---
name: qa
description: QA 独立验证工作流
metadata:
  type: harness-workflow
---

# QA Verification Workflow

## Principle

QA must verify independently of the Engineer. "测试通过" from the Engineer is not sufficient evidence.

## Checklist

### Normal Flow
- [ ] Happy path produces expected output
- [ ] Response shape matches API contract
- [ ] HTTP status codes are correct

### Edge Cases
- [ ] Empty input → 400 with clear error
- [ ] Boundary values (min, max, zero, negative)
- [ ] Null / missing required fields
- [ ] Whitespace-only strings
- [ ] Very long input (truncation / rejection)
- [ ] Unicode / special characters

### Auth & Isolation
- [ ] No bearer token → 401
- [ ] Expired token → 401
- [ ] Revoked token → 401
- [ ] User A cannot access User B's data
- [ ] Public endpoints correctly bypass auth

### Fault Tolerance
- [ ] Downstream service unavailable → graceful degradation
- [ ] Timeout → appropriate error, no hang
- [ ] Retry idempotency (duplicate request = same result)
- [ ] Database constraint violation → handled, not 500

### Regression
- [ ] All existing tests still pass
- [ ] No new warnings in build output
- [ ] git diff --check clean

## Output

```markdown
## QA Report

Task ID: {TASK-ID}
Reviewer: QA Engineer
Date: {date}

### Results

| Check Category | Result |
|---|---|
| Normal Flow | PASS / FAIL / NOT_RUN |
| Edge Cases | PASS / FAIL / NOT_RUN |
| Auth & Isolation | PASS / FAIL / NOT_RUN |
| Fault Tolerance | PASS / FAIL / NOT_RUN |
| Regression | PASS / FAIL / NOT_RUN |

### Failures

{detailed failure descriptions with reproduction steps}

### Evidence

{test commands and their output}

### Verdict

PASS / FAIL / BLOCKED
```
