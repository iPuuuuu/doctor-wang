---
name: authentication-filter-fix-qa
description: AUTH-001 auth-filter public-route repair independent QA evidence
---

# AUTH-001 Filter Repair — QA Report

## Current verdict

**IN_PROGRESS.** Static checks pass, but the required independent Gradle test run is deliberately not yet executed because the Architect reserved the shared Gradle output/cache slot for another active task. This report therefore contains no runtime PASS claim.

## Verification matrix

| Check | Status | Evidence |
| --- | --- | --- |
| Diff whitespace | PASS | `git diff --check` exited 0 with no output. |
| Login without bearer returns 200 | NOT RUN | Covered by `AuthHttpContractTest.login endpoint bypasses auth filter and returns login response`; execution deferred. |
| Anonymous protected profile returns 401 | NOT RUN | Covered by `AuthHttpContractTest.protected profile endpoint returns 401 without bearer token`; execution deferred. |
| Valid persisted-session profile returns 200 | NOT RUN | Covered by `AuthHttpContractTest.protected profile endpoint returns 200 with valid persisted session`; execution deferred. |
| Public path is not accidentally broadened | NOT RUN | Source uses exact equality only; no executable trailing-slash/descendant negative-path assertion was found in the focused tests. |
| Existing service login behavior | NOT RUN | `BackendServiceTest` execution deferred. |

## Static observations

`AuthTokenFilter.shouldNotFilter` removes `contextPath` from `requestURI`, falls back to `servletPath` only if `requestURI` is unavailable, and compares the normalized value exactly with `/api/login`, `/healthz`, and `/`. On source inspection, `/api/login/` and descendant/prefix routes are not in that allow-list.

## Next action

Once the shared Gradle execution slot is released, QA must run:

```bash
cd doctor-wang-mock-server && bash ./gradlew --no-daemon test \
  --tests 'com.ipuuuuu.doctorwang.mock.controller.AuthHttpContractTest' \
  --tests 'com.ipuuuuu.doctorwang.mock.config.AuthTokenFilterTest' \
  --tests 'com.ipuuuuu.doctorwang.mock.service.BackendServiceTest'
```

If it passes, the remaining explicit negative allow-list boundary gap should be reported to Backend Lead for a bounded test decision. AUTH-001 remains release-**BLOCKED** independently of this repair until its existing integration, deployment, and migration gates are satisfied.
