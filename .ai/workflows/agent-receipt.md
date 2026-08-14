---
name: agent-receipt
description: Agent 完成任务后的结构化交付报告模板
metadata:
  type: harness-workflow
---

# Agent Receipt Template

Every Engineer, Lead, QA, and Security Agent MUST produce a structured receipt upon task completion. The receipt is the primary evidence artifact for Architect review.

## Required Format

```markdown
## Agent Receipt

Task ID: {TASK-ID}
Agent: {agent name / role}
Role: Architect | Backend Lead | Backend Engineer | AI Lead | AI Engineer | Android Lead | Android Engineer | QA Engineer | Security Reviewer
Date: {ISO date}

### Status
ACCEPT | REWORK | BLOCKED

### Summary
{2-3 sentences describing what was done}

### Changed Files
- {file path} — {brief description of change}
- {file path} — {brief description of change}

### Tests Run
```
{exact command}
{output summary}
```

### Test Result
PASS / FAIL / NOT_RUN

{number} tests completed, {number} passed, {number} failed

### Evidence
- {link to test output, screenshot, log file}
- {git diff --stat output}
- {specific verification command and result}

### Known Risks
- {risk description} — severity: P0/P1/P2/P3
- {mitigation if any}

### Blockers
- {blocker description} — {required to unblock}

### Unfinished Items
- {item} — {reason not done} — {recommended owner}

### Recommended Next Action
{concrete next step for the next Agent in the chain}
```

## Quality Rules

1. **Never** submit a receipt that only says "完成了" or "测试通过"
2. **Always** include exact test commands and their output
3. **Always** note what was NOT done and why
4. **Always** link to the specific files changed
5. If a check was skipped: state the reason and who should run it later
6. If blocked: include the escalation document
7. Evidence must be reproducible — another Agent should be able to verify independently

## Example (Good)

```markdown
## Agent Receipt

Task ID: FIX-A1
Agent: backend-engineer-1
Role: Backend Engineer
Date: 2026-08-12

### Status
ACCEPT

### Summary
Fixed GlobalExceptionHandler structure bug where handleMissingPart and
handleMissingParameter were nested inside handleSecurity method body,
preventing Spring from registering them.

### Changed Files
- config/GlobalExceptionHandler.kt:48-55 — extracted two @ExceptionHandler
  methods to top-level class scope

### Tests Run
```
bash ./gradlew --no-daemon test
BUILD SUCCESSFUL in 11s
57 tests completed, 0 failed
```

### Test Result
PASS — 57 tests, 0 failures, 0 skipped

### Evidence
- git diff --check PASS
- compileKotlin PASS (only pre-existing JwtTokenService warnings)
- All existing BackendServiceTest, AuthHttpContractTest, AuthTokenFilterTest PASS

### Known Risks
- None — pure structural fix, no behavior change

### Blockers
- None

### Recommended Next Action
QA to verify: send multipart request without file part, confirm 400 + FILE_REQUIRED
