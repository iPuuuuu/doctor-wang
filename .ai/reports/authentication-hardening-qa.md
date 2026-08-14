---
name: authentication-hardening-qa
description: AUTH-001 本轮 JWT 配置与 JOSE 头加固的独立 QA 验证矩阵
metadata:
  type: qa-report
status: BLOCKED
---

# AUTH-001 Authentication Hardening QA Report

Date: 2026-08-09

## Scope and verdict

This QA pass reviewed and executed evidence for the current JWT hardening slice: bounded JWT TTL, strict `alg`/`typ` validation, and mapping an invalid authentication service configuration to a fail-closed 503 response. It did not modify business code.

**Verdict: BLOCKED.** The local unit-test portion is PASS, but the required live PostgreSQL/Flyway V8/Hibernate validation gate cannot start because this environment has no usable Docker socket/runtime. This pass also found that the existing Testcontainers matrix does not assert V8 migration results or JWT session/revocation behavior.

## Verification matrix

| Scenario | Status | Evidence |
|---|---|---|
| JWT issuance retains subject, database user ID and JTI | PASS | `JwtTokenServiceTest.issued token contains verifiable identity and session`; `bash ./gradlew --no-daemon test --rerun-tasks` completed `BUILD SUCCESSFUL`. |
| Modified token is rejected | PASS | `JwtTokenServiceTest.tampered token is rejected`; same backend unit-test command. |
| Invalid JWT TTL of zero or above 24 hours is rejected | PASS | `JwtTokenServiceTest.invalid ttl is rejected at service startup`; `JwtTokenService` enforces `1..86400`. |
| Unexpected JOSE `alg` is rejected | PASS | `JwtTokenServiceTest.unexpected jose header is rejected`; parser requires `alg=HS256` and `typ=JWT`. |
| Correct/incorrect BCrypt password verification; raw password not stored in hash | PASS | `PasswordHasherTest.hash verifies without storing raw password`; backend unit-test command. |
| Backend compilation/static whitespace | PASS | `bash ./gradlew --no-daemon test --rerun-tasks` compiled Kotlin successfully; `git diff --check` exited 0. Compilation produced three non-fatal warnings: an unchecked JWT-map cast and two nullable `JdbcTemplate.queryForObject` type mismatches. |
| Android Gradle regression | PASS (build only) | `bash ./gradlew test` at repository root exited 0. `testDebugUnitTest` and `testReleaseUnitTest` were `NO-SOURCE`; this is not behavioral evidence for login or 401 session clearing. |
| PostgreSQL/Flyway V8 execution and Hibernate `ddl-auto=validate` startup | BLOCKED | `bash ./gradlew --no-daemon postgresIntegrationTest --stacktrace` failed before test body: `Could not find a valid Docker environment`; `/var/run/docker.sock` is absent. XML: `doctor-wang-mock-server/build/test-results/postgresIntegrationTest/TEST-com.ipuuuuu.doctorwang.mock.integration.PostgresIntegrationMatrixTest.xml`. |
| V8 schema/session migration assertions | NOT RUN | The sole Testcontainers class is unable to start. Static review also shows its Flyway assertion only checks versions `1..7`, so a Docker-capable rerun would still not prove V8 has applied or that `password_hash`, `jti`, `token_hash(64)`, `revoked_at`, and legacy-session deletion meet the approved contract. |
| HTTP login contract: valid login, generic invalid credentials, empty input, repeat submit | NOT RUN | No controller/MockMvc test source located. Unit tests do not invoke `POST /api/login`. |
| Filter contract: no bearer, expired/tampered token, issuer/audience/JTI mismatch, hash mismatch, revoked JTI, user mismatch | NOT RUN | No executable filter/session test is present. Source inspection is not equivalent to an HTTP or persistence test. |
| Authentication configuration failure maps to safe 503 | NOT RUN | Source implements `writeServiceUnavailable`, but no MockMvc/filter test demonstrates 503 and safe error body. |
| Server-side logout/revoke behavior | NOT RUN / product gap | `revoked_at` is checked by the filter, but no authenticated revoke/logout endpoint was introduced; a stolen JWT remains usable until expiry unless an out-of-band database operation revokes it. |
| Android password semantics, 401 DataStore clear, no token/body logging, release transport | NOT RUN | No Android unit/UI/security test source is present. Root Gradle test has no test classes. |

## Exact commands and results

- `cd doctor-wang-mock-server && bash ./gradlew --stop; bash ./gradlew --no-daemon test --rerun-tasks` — **PASS**, `BUILD SUCCESSFUL in 11s`; five actionable tasks executed. The initial parallel QA invocation hit a Kotlin incremental-cache/daemon collision, then this serialized re-run passed. The collision is an execution-environment concurrency issue, not a product-test failure.
- `cd doctor-wang-mock-server && bash ./gradlew --no-daemon postgresIntegrationTest --stacktrace` — **BLOCKED**. One initialization error occurred before test execution because Testcontainers found neither Docker CLI/socket nor a valid provider.
- `bash ./gradlew test` (repository root) — **PASS (build only)**; Android test tasks report `NO-SOURCE`.
- `git diff --check` — **PASS**, exit code 0.

## QA findings and required follow-up

1. **P1 release gate — BLOCKED:** Run the PostgreSQL matrix in Docker/Podman-capable CI, archive the PASS report, and confirm Flyway V8 plus Hibernate validation. This environment cannot establish that database migration safety.
2. **P2 test-gap:** Extend `PostgresIntegrationMatrixTest` (or a focused equivalent) to assert V8 is applied and its precise contract: hashed demo credential, legacy-session invalidation, `token_hash varchar(64)`, JTI uniqueness, and revoked session rejection. Its present `flyway v1 through v7` test name/assertion is stale for AUTH-001 and insufficient for V8.
3. **P2 test-gap:** Add HTTP/filter tests for login generic error behavior, 401 paths, expiry/issuer/audience/JTI/session/hash/user mismatch, revoked sessions, and the 503 configuration-failure mapping. Exercise normal and repeated requests without logging secrets.
4. **P2 product/security gap:** Add an authenticated user-scoped revoke/logout flow, or record Architect-approved bounded-TTL and incident-response risk acceptance. QA cannot mark revocation as verified without an executable path.
5. **P2 Android evidence gap:** Add focused unit/UI tests for password input, loading/repeat-submit behavior, 401 session clearing, and release cleartext/logging constraints.

No real patient data, credentials, tokens, or runtime databases were accessed by this QA pass.
