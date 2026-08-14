## Handoff
- Task ID: AUTH-001
- Agent: Security Reviewer
- Status: BLOCKED
- Scope changed: None; read-only review of JWT issuance/verification, credential hashing, session persistence/revocation, migration V8, Android token handling/logging, deployment and authentication documentation.
- Files changed: `.ai/handoffs/AUTH-001/security-reviewer.md`, `.ai/reports/authentication-security-review.md` (review evidence only; no business code changed).
- Decisions: Release approval is blocked. The current V8 migration correctly deletes legacy sessions and aligns `token_hash` to 64 characters, so the earlier raw-token-retention/type-mismatch findings are RESOLVED in the current diff. Remaining production transport, credential migration, configuration, revocation and verification gaps require remediation or explicit risk acceptance.
- Tests and exact commands:
  - `bash ./gradlew test` (cwd `doctor-wang-mock-server`) — PASS, `BUILD SUCCESSFUL`.
  - `bash ./gradlew postgresIntegrationTest` (cwd `doctor-wang-mock-server`) — BLOCKED/FAIL: Testcontainers `DockerClientProviderStrategy` initialization; Docker runtime unavailable.
  - `bash ./gradlew test --tests 'com.ipuuuuu.doctorwang.mock.integration.PostgresIntegrationMatrixTest'` — NOT RUN as an integration test (default `test` excludes `integration`; Gradle reports no matching tests).
  - `APP_AUTH_JWT_SECRET='01234567890123456789012345678901' docker compose -f deploy/docker-compose.yml config --quiet` — NOT RUN; Docker CLI is not installed (`command not found: docker`).
  - `git diff --` and repository searches for Authorization/Bearer/password/body logging/cleartext configuration — completed read-only.
  - Android unit/UI/security tests — NOT RUN; no `app/src/test` or `app/src/androidTest` test sources were found.
- Results and evidence:
  - JWT signature is fixed HS256 HMAC with issuer/audience/subject/uid/JTI/expiry checks (`doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/JwtTokenService.kt:47-69`); session lookup binds JTI, database ID, user ID, SHA-256 token hash, expiry and `revoked_at` (`.../config/AuthTokenFilter.kt:40-46`).
  - Password verification uses BCrypt (`.../service/PasswordHasher.kt`); login returns one generic authentication failure for unknown account/wrong password (`.../service/BackendService.kt:44-60`).
  - V8 deletes all pre-V8 sessions before renaming and shrinking the token column, then adds JTI/revocation (`.../db/migration/V8__harden_user_credentials_and_jwt_sessions.sql:12-23`). This invalidates legacy sessions but must be proven against PostgreSQL with `ddl-auto=validate`.
  - Android BODY logging was removed and HTTP 401 clears DataStore session (`app/src/main/java/com/ipuuuuu/doctorwang/network/NetworkProvider.kt:19-36`). No source-level BODY logger or Authorization logging was found.
- Risks / privacy / quality concerns:
  - **P1 BLOCK — Production deploy is HTTP-only.** `deploy/docker-compose.yml` publishes Nginx port 80 only and `deploy/nginx/default.conf` has no TLS listener/redirect. `deploy/README.md:94-102` explicitly says HTTPS is not configured. Bearer JWTs and health data can be intercepted on an untrusted network. Do not release to real users until TLS is enforced end-to-end or a controlled TLS-terminating proxy is documented and verified.
  - **P1 BLOCK — V8 is not a safe general-user credential migration.** It populates `password_hash` only for `demo@doctorwang.app` with a fixed demo BCrypt value, sets NOT NULL, and drops plaintext `password` (`V8...sql:1-10`). Any non-demo account causes migration failure/data loss unless an approved pre-migration reset/backfill is run. The V2 migration also stores demo plaintext `123456` in repository history (`V2__seed_demo_user.sql:1-2`). Remove demo credentials from production path and provide a controlled, backup-tested reset/backfill runbook.
  - **P2 — Revocation is data-model-only.** `revoked_at` is enforced by the filter, but there is no authenticated logout/revoke API; Android logout only deletes its local token (`RemoteDoctorWangRepository.kt:48-51`). A stolen JWT remains usable until expiry. Add a user-scoped revoke endpoint or explicitly accept/document the limitation with bounded TTL and incident response.
  - **P2 — JWT TTL is unconstrained.** `AuthProperties` accepts zero, negative, or arbitrarily long values; `JwtTokenService.issue` does not reject them. Add startup validation (positive, bounded TTL) and tests.
  - **P2 — Configuration errors are hidden as 401.** `AuthTokenFilter` catches every exception from `verify`, including `AUTH_CONFIGURATION_INVALID`, and returns `AUTHENTICATION_REQUIRED` (`AuthTokenFilter.kt:40`). This fails closed but hides a deployment misconfiguration and prevents reliable alerting/503 behavior. Separate configuration exceptions and emit only safe operational telemetry.
  - **P2 — Source/runtime logging and Android release transport remain incompletely evidenced.** Search found no BODY logger, but no release APK inspection, proxy access-log redaction check, or production observability test was run. Android debug explicitly permits cleartext (`app/src/debug/AndroidManifest.xml`); ensure this cannot enter release and prohibit HTTP server URLs in release configuration.
  - **P3 — JWT parser does not explicitly reject an unexpected JOSE header algorithm/type.** HMAC verification prevents unsigned-token acceptance, but strict `alg=HS256`/`typ=JWT` validation is defense-in-depth and should be tested.
- Open questions:
  - Is invalidating every existing session at V8 approved, and what is the production password reset/backfill procedure for non-demo users?
  - Which authenticated endpoint/administrative workflow revokes a JTI after theft?
  - What production TLS termination, JWT secret rotation/key versioning and TTL limits are approved?
- Next owner: Backend Lead/Engineer for migration, TTL, strict parser and revoke-path remediation; Deployment owner for TLS; QA for Docker/Testcontainers and Android release evidence; Architect for final risk acceptance.

## Security conclusion

`BLOCK` — unit tests pass and the current session hash migration is safer than the prior draft, but HTTP-only deployment and unsafe/demo-only credential migration are release-blocking. PostgreSQL/Testcontainers and Android security evidence are unavailable in this environment.
