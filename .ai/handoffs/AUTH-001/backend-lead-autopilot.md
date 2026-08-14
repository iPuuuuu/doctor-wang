## Handoff
- Task ID: AUTH-001
- Agent: Backend Lead
- Status: BLOCKED
- Scope changed: Read-only review; no production or test source changed.
- Files changed: `.ai/handoffs/AUTH-001/backend-lead-autopilot.md` only.
- Decisions:
  - The previously selected no-Docker `BackendService.login(...)` unit-coverage slice is already present and locally passes. It covers trimmed account lookup, generic unknown-account and bad-password failures, JWT issuance, a hashed (not raw) persisted token, JTI, linked user, expiry, and non-revoked initial session.
  - JWT verification and session filter behavior are also covered by the existing focused tests: required HS256/typ header and claims, expiry, issuer/audience, malformed input, missing/malformed bearer token, revoked/expired persisted sessions, valid-session context setup, and configuration failure mapping to 503.
  - The next smallest dependency-satisfied backend slice is an HTTP contract regression for `POST /api/login`: correct public-route bypass of `AuthTokenFilter` while retaining 401 protection for non-public routes. This is a test/code correction confined to the existing filter and its controller contract test; it neither changes API shape nor schema/migrations.
- Tests and exact commands:
  - `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests 'com.ipuuuuu.doctorwang.mock.service.BackendServiceTest'` — PASS (3 tests).
  - `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests '*BackendServiceTest' --tests '*AuthHttpContractTest'` — FAIL. `AuthHttpContractTest.login endpoint bypasses auth filter and returns login response()` expected 200 but got 401. The same run reports the three `BackendServiceTest` cases passing; its first Kotlin-daemon cache close error fell back to non-daemon compilation and is not the functional failure.
- Results and evidence:
  - `BackendService.login` calls `findByAccount(account.trim())`, returns only the generic `AUTHENTICATION_FAILED` error for absent account/bad password, calls the password hasher before issuing JWT, and persists `UserSessionEntity(jti, tokenHash, user, expiresAt)` in one `@Transactional` method.
  - `AuthTokenFilter.shouldNotFilter` permits `/api/login`, `/healthz`, and `/`, but the standalone MVC login contract currently reaches the filter and is rejected before `AuthController`. This is direct executable evidence that the public login route is not proven to work under the current request-path handling.
  - `V8__harden_user_credentials_and_jwt_sessions.sql` remains unverified against live PostgreSQL because Testcontainers cannot find Docker (existing task-level blocker); no migration change is proposed.
- Risks / security / medical concerns:
  - P1 functional/authentication regression: a valid login request can receive 401 if the deployed servlet path exhibits the observed matching semantics. Do not mark AUTH-001 accepted while this contract is failing.
  - Live PostgreSQL migration/session persistence remains BLOCKED by absent Docker; HTTP-only deployment, demo credential migration, Android transport/logging evidence, and logout/revoke product decision remain task-level security gates.
- Open questions:
  - Backend Engineer must establish the intended Spring request-path source (`servletPath`, `requestURI` after context path, or Spring path utility) and verify it consistently excludes exactly the public endpoints without widening the allow-list.
- Next owner: Backend Engineer for the narrowly scoped filter public-route matching fix plus focused regression tests; then QA for independent rerun and Backend Lead review.

## Proposed minimal implementation package

- Goal: Make the existing `POST /api/login` HTTP contract pass without reducing protection of `/api/profile` or other authenticated routes.
- Allowed files: `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/AuthTokenFilter.kt`; `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/controller/AuthHttpContractTest.kt`; only an additional focused filter test if necessary. No controller API, entity, repository, Flyway, Gradle, Android, deployment, or unrelated files.
- Acceptance criteria:
  1. `POST /api/login` reaches `AuthController` without an Authorization header and returns the service response (200 in the existing contract).
  2. `GET /api/profile` without a bearer token remains 401 and does not invoke `BackendService`.
  3. A valid persisted bearer session still reaches the protected controller (200 in the existing contract).
  4. Exact focused command passes: `bash ./gradlew --no-daemon test --tests 'com.ipuuuuu.doctorwang.mock.controller.AuthHttpContractTest' --tests 'com.ipuuuuu.doctorwang.mock.config.AuthTokenFilterTest'`.
  5. Existing `BackendServiceTest` remains PASS and no API/database contract changes are introduced.
