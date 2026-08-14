## Handoff
- Task ID: AUTH-001
- Agent: Quality / Security Reviewer (final method-lockdown + logout/revoke review)
- Status: READY_FOR_REVIEW
- Scope changed: Independent read-only review of the current exact path-and-method `AuthTokenFilter` change and the authenticated `/api/logout` / JTI-revocation slice. No business, Android, deployment, migration, or test source was modified by this review.
- Files changed:
  - `.ai/handoffs/AUTH-001/quality-final-auth-review.md`
- Decisions:
  - **Method-lockdown sub-slice: ACCEPT.** `shouldNotFilter` now permits only `POST /api/login` and `GET /healthz`/`GET /`; it derives an application-relative path from `requestURI` and `contextPath`, uses exact equality, and does not permit descendants, trailing slashes, or unsupported methods. The filter therefore no longer has the previously reported method-agnostic public bypass.
  - **Logout/revoke sub-slice: REWORK.** The endpoint is correctly kept behind the authentication filter, binds revocation to the verified JTI and database user ID, and makes repository update idempotent with `coalesce`. However, the current HTTP contract tests mock `BackendService`, so they prove filter/controller routing but not that a real request executes the repository revoke and that a subsequent protected request is rejected. This is required evidence for the trust-boundary change, even though the focused unit tests pass.
  - Combined bounded-slice verdict: **REWORK** pending a real service/repository HTTP or integration regression proving post-logout rejection (and user/JTI binding). This is a sub-slice verdict only and must not supersede the parent AUTH-001 release `BLOCK`.
- Tests and exact commands:
  - `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests 'com.ipuuuuu.doctorwang.mock.config.AuthTokenFilterTest' --tests 'com.ipuuuuu.doctorwang.mock.controller.AuthHttpContractTest' --tests 'com.ipuuuuu.doctorwang.mock.service.BackendServiceTest'` — **PASS**, `BUILD SUCCESSFUL` (all three focused classes; 9s).
  - `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && bash ./gradlew --no-daemon test` — **PASS**, `BUILD SUCCESSFUL` (full configured backend unit/contract suite; 7s).
  - `git diff --check` — **PASS** (no whitespace errors).
  - `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && bash ./gradlew --no-daemon postgresIntegrationTest --stacktrace` — **BLOCKED / NOT RUN** at test body: Testcontainers cannot find a Docker runtime/socket in this environment (existing project evidence; local `docker version` returned `command not found: docker`).
  - Android test/build, release APK network-policy inspection, and runtime proxy/access-log redaction — **NOT RUN** in this review; root `bash ./gradlew test` previously reports Android unit task `NO-SOURCE`.
- Results and evidence:
  - **PASS / P2 finding closed:** `AuthTokenFilter.kt` checks method plus exact application-relative path. Regression tests cover context path `/gateway`, wrong methods (`GET /api/login`, `POST /healthz`, `HEAD /`), trailing slash and descendant paths; these all return 401 without JWT/session interaction.
  - **PASS:** Protected requests verify JWT, JTI lookup, token hash, subject, database user ID, session expiry, and revoked state before setting `AuthContext`. A revoked session can reach only exact `POST /api/logout`; all other routes remain 401. Error responses are fixed generic codes with trace ID and do not serialize bearer material or persistence details.
  - **PASS (unit):** `BackendService.logout` uses `AuthContext.sessionId` and `authenticated.user.id` in `revokeByJtiAndUserId`; repository update uses `coalesce` to preserve first revocation timestamp and returns zero rows safely on repeat. Backend service tests verify matcher-safe arguments and idempotent invocation.
  - **PASS (contract shape):** `AuthController` exposes `POST /api/logout` with 204, and Android `RemoteDoctorWangRepository.logout` attempts server revoke then unconditionally clears local session on any failure. This preserves local logout safety but has no Android automated evidence.
  - **P2 / REWORK:** `AuthHttpContractTest`'s valid and repeated logout cases stub `userSessions` and mock `BackendService`; they do not execute `BackendService.logout` against a repository, nor perform a follow-up protected request after revocation. Add one executable HTTP/integration path that logs in or seeds a session, posts logout, observes repository revocation, and confirms the same bearer receives 401 on `/api/profile`; include a cross-user/JTI mismatch rejection case.
  - **P2 / existing parent blocker:** `SessionManager.saveServerUrl` accepts arbitrary schemes and the Settings UI persists them; release transport policy has no source/test enforcement at this input boundary. A user-configured `http://` endpoint can still be selected. This remains outside the bounded backend slice but blocks production security acceptance.
  - **P1 / existing parent blocker:** production deployment and migration evidence remain unverified. Current Nginx/Compose now contain 443 TLS/redirect and required secrets, and V8 uses PostgreSQL `crypt` for all rows while deleting legacy sessions, but Docker-capable Compose/Flyway execution, backup/restore, and non-demo migration runbook evidence are still absent. Do not call these controls verified from static diff alone.
  - **P3:** Source search found no body/bearer logging in reviewed production paths; runtime Nginx/application log redaction and release APK inspection are **NOT RUN**.
- Risks / privacy / quality concerns:
  - No P0 found. Authentication context is cleared in `finally`; logout update is user/JTI scoped and does not accept a client-supplied identifier. The broad `catch (Exception)` in Android logout intentionally fails closed locally (session cleared) but hides server-revoke observability and has no telemetry evidence.
  - Existing parent P1/P2 findings (TLS/migration live proof, URL scheme enforcement, runtime logging evidence) remain release-blocking or unverified; this handoff cannot alter the parent gate.
- Open questions:
  - QA/Backend Lead should add and run the post-revoke HTTP/integration regression in a Docker-capable environment.
  - Product/Architect must decide whether a failed remote revoke needs user-visible/telemetry signaling while still clearing local credentials.
- Next owner: Backend Engineer adds the real post-revoke HTTP/integration test; QA reruns it and the full suite; Quality Reviewer performs a follow-up. Architect retains parent AUTH-001 release decision.

## Review Conclusion

**REWORK** for the combined method-lockdown + logout/revoke bounded slice solely because end-to-end post-revocation evidence is missing. Method-lockdown itself is accepted. Parent AUTH-001 remains **BLOCK** due to its recorded P1/P2 deployment, migration, Android transport, and Docker/integration gates.
