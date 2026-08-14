## Handoff

- Task ID: AUTH-001
- Agent: QA Engineer
- Status: VERIFIED
- Scope changed: Read-only QA review of the stateful HTTP logout regression handoff and `AuthHttpContractTest` additions. No production, migration, deployment, or test files changed.
- Files changed: `.ai/handoffs/AUTH-001/qa-stateful-revoke-followup.md`
- Decisions:
  - The new stateful test is valid bounded HTTP/filter regression evidence: it constructs `AuthController` with a real `BackendService`, a controlled in-memory `UserSessionRepository` double, and the real `AuthTokenFilter`.
  - The logout request reaches `BackendService.logout`; the double applies the JTI/user-scoped revoke update; the same bearer is then sent to `/api/profile` and is rejected with HTTP 401. This closes the previously identified HTTP-level gap for the service/filter interaction.
  - The cross-user/JTI test correctly keeps a victim session under a different user id while presenting an attacker JWT, expects 401, verifies the victim remains unrevoked, and separately rejects an unknown JTI without creating state.
  - Existing repeated-logout coverage remains a contract/idempotency check for an already revoked current session (mocked service path). It is useful, but is not equivalent to two sequential real repository-backed HTTP requests.
- Tests and exact commands:
  - Reviewed backend handoff: `.ai/handoffs/AUTH-001/backend-engineer-stateful-logout-http-regression.md`.
  - Reviewed `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/controller/AuthHttpContractTest.kt` lines 164-218 and helper state double lines 229-245.
  - Handoff-reported command `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests 'AuthHttpContractTest.real backend logout revokes bearer and filter rejects it on the next protected request'` — PASS.
  - Handoff-reported command `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests '*AuthHttpContractTest'` — PASS.
  - Handoff-reported command `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test` — PASS.
  - Handoff-reported command `git diff --check` — PASS.
- Results and evidence:
  - PASS: logout response is 204 for a current valid session in the stateful regression.
  - PASS: the in-memory state has a non-null `revokedAt` after the real service call.
  - PASS: the following protected request using the same bearer returns 401, demonstrating filter enforcement of the changed session state.
  - PASS: same-JTI/different-user and unknown-JTI logout attempts return 401 and do not revoke or create session state.
  - PASS: test data is synthetic/de-identified; no patient data, production credentials, or runtime tokens are persisted.
- Risks / privacy / quality concerns:
  - This evidence is intentionally limited to a controlled repository double. It does **not** prove JPA update-query behavior, transaction/flush semantics, Flyway V8 execution, PostgreSQL constraints, Hibernate validation, or persistence across requests/processes.
  - Docker/Testcontainers-backed `postgresIntegrationTest` remains BLOCKED in the current environment because no Docker runtime is available. The release gate must remain blocked for the real persistence criterion until CI or an equivalent PostgreSQL environment supplies that evidence.
  - The repeated-logout case is not a two-request stateful sequence; consider adding that assertion when real repository integration infrastructure is available, without treating the current mock test as database proof.
- Open questions:
  - Run PostgreSQL/Testcontainers integration and deployment TLS/Nginx smoke tests in Docker-capable CI, then obtain independent QA and Quality Reviewer re-review.
- Next owner: Quality Reviewer / Architect

## QA Decision

- Stateful HTTP/filter logout regression sub-slice: **PASS**.
- AUTH-001 overall release acceptance: **BLOCKED** pending real PostgreSQL/Flyway and deployment/runtime evidence; this handoff does not remove that blocker.
