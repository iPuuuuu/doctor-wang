## Handoff
- Task ID: AUTH-001
- Agent: Security Reviewer
- Status: BLOCKED
- Scope changed: None (independent read-only review of JWT authentication, credential hashing, JTI sessions, Android 401/logging behavior, V8 migration, and deployment configuration)
- Files changed: `.ai/handoffs/AUTH-001/security.md` only
- Decisions: Do not approve the authentication vertical slice for release until the migration/session findings below are remediated and PostgreSQL/Flyway plus Android security checks have executable evidence.
- Tests and exact commands:
  - `bash ./gradlew test` (working directory `doctor-wang-mock-server`)
  - `rg -n "HttpLoggingInterceptor|Level\\.BODY|Log\\.|println\\(|Authorization|Bearer|password" app/src/main doctor-wang-mock-server/src/main --glob '!**/build/**'`
  - `git diff --` on all AUTH-001 implementation, migration, Android, and deploy files
  - PostgreSQL/Testcontainers integration: NOT RUN (Docker/Testcontainers runtime was unavailable in this environment)
  - Android unit/UI tests: NOT RUN / no Android test sources are present in the repository
- Results and evidence:
  - Backend unit test command completed `BUILD SUCCESSFUL` (5 actionable tasks); it does not execute the tagged Testcontainers integration matrix.
  - Android network layer no longer contains a BODY logging interceptor (`app/.../network/NetworkProvider.kt:7-37`); it clears the local session on HTTP 401 (`:29-32`). No other BODY logger was found by the repository search.
  - JWT verification checks HS256-derived signature, issuer, audience, subject, database user id, expiry, JTI, token hash, session expiry, and `revoked_at` (`doctor-wang-mock-server/src/main/kotlin/.../JwtTokenService.kt:47-69`, `.../config/AuthTokenFilter.kt:40-45`). Password verification uses BCrypt (`.../service/PasswordHasher.kt`).
- Risks / security / medical concerns:
  - **P1 — Existing bearer tokens remain stored in cleartext after V8.** `V8__harden_user_credentials_and_jwt_sessions.sql:12` only renames `user_session.token` to `token_hash`; it never hashes or deletes pre-existing opaque token values. The filter compares SHA-256(token) to this column, so legacy sessions are rejected, but the old bearer secrets remain recoverable at rest and the column is mislabeled. Remediate with a migration that invalidates/deletes legacy sessions before/while changing the schema (or performs a verified one-way hash), then prove no raw token remains.
  - **P1 — V8 leaves the renamed token column as `varchar(255)` while the entity requires `token_hash varchar(64)`.** `V8...sql:12` performs no type alteration; `UserSessionEntity.kt` declares `@Column(... length = 64)` (`:19-24`). With `spring.jpa.hibernate.ddl-auto=validate` (`application.yml:22-25`), PostgreSQL startup may fail schema validation. Run a real PostgreSQL migration/startup test and explicitly alter the column to `varchar(64)` (or align the entity) before release.
  - **P2 — Revocation is represented but no authenticated logout/revoke operation exists.** `revoked_at` is checked by the filter (`AuthTokenFilter.kt:44`), but `/api` exposes only `/login` (`controller/AuthController.kt:16-20`), and Android logout only clears DataStore locally (`RemoteDoctorWangRepository.kt:48-51`). A stolen JWT cannot be actively revoked through the product/API. Add an authenticated, user-scoped revoke endpoint/service (or document and accept this limitation) and test that a revoked JTI is rejected.
  - **P2 — JWT TTL accepts unsafe values.** `AuthProperties.kt:6-10` permits zero/negative/very large `jwtTtlSeconds`; `JwtTokenService.kt:27-30` issues immediately expired tokens for non-positive values and has no upper bound. Add startup/config validation with a bounded positive TTL appropriate for the threat model and a test for invalid configuration.
  - **P2 — Migration credential handling is demo-only and not a production migration path.** `V8...sql:5-7` installs one hard-coded demo BCrypt hash and drops the plaintext column (`:9-10`); non-demo rows become NULL and cause `set not null` to fail. The ADR documents controlled production reset, but release evidence must show a separately approved production migration/runbook and a rollback/backup plan; never run this unchanged against a database containing real users.
  - **P3 — Configuration errors are collapsed into 401 in the filter.** `AuthTokenFilter.kt:40` catches every exception from `verify`, including `AUTH_CONFIGURATION_INVALID` thrown when the secret is too short (`JwtTokenService.kt:79-81`), and returns `AUTHENTICATION_REQUIRED`. This fails closed but obscures an operational security misconfiguration and bypasses the intended 503 mapping; distinguish configuration failures and alert without exposing secrets.
  - No evidence of Authorization/body logging was found in the reviewed Android/backend source. This is source-level evidence only; release logging configuration and proxy/access-log redaction remain unverified.
- Open questions:
  - Is AUTH-001 explicitly allowed to invalidate all existing sessions, and what controlled production credential reset process is approved for non-demo users?
  - What endpoint or administrative control is the intended session revocation path?
  - Which bounded JWT TTL and secret rotation/key-version policy will production use?
- Next owner: Backend Engineer/Lead to correct V8 and add revocation/config tests; QA to execute PostgreSQL/Testcontainers and Android tests; Architect to review remediation and make the final ACCEPT/REWORK/BLOCK decision.

## Security conclusion

`BLOCK` for release: the unit tests pass, but raw legacy bearer values are retained by the migration, schema/type compatibility is unproven and likely invalid under `ddl-auto=validate`, and active revocation plus production migration evidence are incomplete. No P0 was observed in the reviewed diff; the P1 migration issues are release-blocking until independently verified.

## Focused re-review addendum — 2026-08-09

- Reviewed correction: `doctor-wang-mock-server/src/main/resources/db/migration/V8__harden_user_credentials_and_jwt_sessions.sql:12-23`.
- **P1 legacy-token-at-rest finding: statically remediated.** The migration now executes `delete from user_session` before renaming the column (`:15-16`), so pre-V8 opaque bearer values are invalidated and removed rather than retained under a misleading `token_hash` name. This is consistent with ADR-0002's requirement that old opaque sessions become invalid and clients re-authenticate. PostgreSQL execution/rollback evidence is still required.
- **P1 schema/type finding: statically remediated.** The migration now explicitly changes `token_hash` to `varchar(64)` (`:17`), matching `UserSessionEntity`'s `length = 64` declaration (`UserSessionEntity.kt:22-23`) and `JwtTokenService.tokenHash()`'s 64-hex-character output. A real Flyway + Hibernate `ddl-auto=validate` startup remains NOT RUN.
- No new issue was introduced by this correction in static review. `delete from user_session` is intentionally destructive to all existing sessions; it is acceptable only under the already documented re-login decision and must be covered by backup/rollback and production change approval. Flyway/PostgreSQL transaction behavior should be verified before release.
- Remaining release gates are unchanged: authenticated server-side revoke/logout path (P2), bounded positive JWT TTL and configuration validation (P2), safe non-demo credential migration/runbook (P2), configuration-error observability (P3), Android test evidence, and PostgreSQL/Testcontainers execution.
- **Updated conclusion: `BLOCK`.** The two previously reported P1 issues are statically corrected, but the live database migration and remaining security gates have not been independently verified.
