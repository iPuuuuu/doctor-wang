---
name: authentication-filter-regression-qa
description: AUTH-001 无 Docker 后端认证回归验证补充报告
metadata:
  type: project
status: READY_FOR_REVIEW
---

# AUTH-001 Authentication Regression QA Report

Date: 2026-08-10

## Scope and verdict

This QA pass verifies the highest-value no-Docker backend regression slices available for AUTH-001: request filtering behavior for missing/invalid/expired/revoked/valid bearer sessions and `BackendService.login(...)` success/failure/session-persistence semantics. It does not change production runtime code.

**Verdict: READY_FOR_REVIEW for these slices, but AUTH-001 overall remains BLOCKED.** The local backend unit suite now includes focused filter and login-path coverage, but Docker-backed PostgreSQL/Flyway execution, P1 transport controls, and production-safe credential migration are still unresolved.

## Verification matrix

| Scenario | Status | Evidence |
|---|---|---|
| Missing bearer token returns stable 401 | PASS | `AuthTokenFilterTest.request without authorization is rejected with stable error` |
| Malformed bearer token returns 401 | PASS | `AuthTokenFilterTest.malformed bearer token is rejected with 401` |
| Expired/invalid bearer token from verifier returns 401 | PASS | `AuthTokenFilterTest.expired bearer token is rejected with 401` |
| Authentication configuration failure returns safe 503 | PASS | `AuthTokenFilterTest.configuration failure returns 503 and does not query sessions` |
| Revoked persisted session returns 401 | PASS | `AuthTokenFilterTest.revoked session is rejected with 401` |
| Expired persisted session returns 401 | PASS | `AuthTokenFilterTest.expired persisted session is rejected with 401` |
| Valid persisted session proceeds through filter and clears `AuthContext` after chain | PASS | `AuthTokenFilterTest.valid session sets auth context and proceeds` |
| Unknown account returns generic authentication failure | PASS | `BackendServiceTest.login rejects unknown account with generic authentication error` |
| Wrong password returns generic authentication failure | PASS | `BackendServiceTest.login rejects invalid password with generic authentication error` |
| Successful login trims account, issues JWT, hashes token for persistence, saves session, and returns token/userId | PASS | `BackendServiceTest.login trims account persists hashed session and returns token response` |
| Full backend unit suite after auth regression additions | PASS | `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --rerun-tasks` → `BUILD SUCCESSFUL in 9s` |
| Diff whitespace hygiene | PASS | `git diff --check` exit 0 |
| Docker-backed PostgreSQL/Flyway V8 execution | BLOCKED | No Docker runtime/socket in current environment |
| Controller-level HTTP contract evidence beyond existing local coverage | NOT RUN in this slice | No new `AuthHttpContractTest` execution recorded in this slice |
| Android auth instrumentation / release transport evidence | NOT RUN | Android SDK/instrumentation environment unavailable in this slice |

## Exact commands and results

- `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests com.ipuuuuu.doctorwang.mock.config.AuthTokenFilterTest` — **PASS**, `BUILD SUCCESSFUL in 5s`.
- `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests com.ipuuuuu.doctorwang.mock.service.BackendServiceTest` — **PASS**, `BUILD SUCCESSFUL in 5s`.
- `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && bash ./gradlew --no-daemon test --rerun-tasks` — **PASS**, `BUILD SUCCESSFUL in 9s`.
- `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && git diff --check` — **PASS**.

## Notes and remaining gaps

- The backend unit run still reports two existing unchecked Kotlin casts in `JwtTokenService.kt` when decoding JWT header/claims maps; these warnings did not fail the build and were not changed in this slice.
- This slice materially improves request-filter and login-service trust-boundary evidence without Docker, but it does **not** prove Flyway V8 migration safety, legacy-session invalidation in PostgreSQL, or the release security of `deploy/`.
- Next highest-value executable options are:
  1. run and, if needed, extend `AuthHttpContractTest` for no-Docker controller/filter HTTP contract evidence; or
  2. Android auth UI instrumentation if an SDK-capable environment becomes available.
