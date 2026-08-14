---
name: authentication-hardening-security-review
description: AUTH-001 focused independent security re-review of JWT hardening
metadata:
  type: security-report
status: BLOCK
---

# AUTH-001 JWT Hardening — Security Re-review

## Scope and conclusion

This is an independent, read-only re-review of the current working tree's focused JWT-hardening changes: bounded TTL, JOSE header verification, authentication configuration failures, and token/logging exposure. It does not replace the existing full review in `.ai/reports/authentication-security-review.md`.

**Conclusion: BLOCK for AUTH-001 release.** The focused implementation is statically improved and the four changes below address the prior targeted findings. QA independently reports that the backend unit suite passed, but AUTH-001 still has unresolved P1 release gates: HTTP-only deployment and a demo-only/non-general-user credential migration. PostgreSQL/Flyway execution remains **BLOCKED**, not PASS.

## Confirmed focused controls (static evidence)

- **TTL bound:** `JwtTokenService` rejects `jwtTtlSeconds` outside 1–86,400 seconds before issuing a token (`doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/service/JwtTokenService.kt:27-31,101-104`). The configured default remains 3,600 seconds (`src/main/resources/application.yml:33-36`).
- **JOSE header allow-list:** verification decodes the protected header and requires exactly `alg=HS256` and `typ=JWT` before it verifies the signature and claims (`JwtTokenService.kt:53-83`). This closes the previous defense-in-depth gap for unexpected `alg`/`typ` values.
- **Configuration fail-fast behavior:** `AuthProperties` rejects a secret shorter than 32 characters, blank/invalid issuer or audience, and TTL outside 1–86,400 during construction. The runtime signature guard remains defensive; an independent filter-level 503 test is now NOT RUN because invalid properties cannot be constructed after fail-fast validation. The Compose deployment requires `APP_AUTH_JWT_SECRET` at interpolation time (`deploy/docker-compose.yml:26-35`).
- **No source-level bearer/body logging found:** the Android network provider adds the Bearer header but does not install a logging interceptor (`app/src/main/java/com/ipuuuuu/doctorwang/network/NetworkProvider.kt:22-38`). Repository search found no `HttpLoggingInterceptor`, `Level.BODY`, `Authorization` logging, `println`, or `Log.*` call in the reviewed backend/Android production sources. This is source-only evidence, not a production logging guarantee.

## Findings

### P1 — BLOCK — TLS is still absent from the deployable public entry point

- **Location:** `deploy/docker-compose.yml:39-48`; `deploy/nginx/default.conf`; `deploy/README.md:94-102`.
- **Issue / impact:** Nginx publishes only `80:80`; the reviewed deployment configuration has no TLS listener or HTTP-to-HTTPS redirect. JWT bearer credentials and health data can be intercepted or modified on an untrusted network.
- **Evidence:** Static configuration review; no alternate TLS-terminating proxy or verified enforcement configuration is recorded.
- **Recommendation:** Require TLS at the public entry point, enforce HTTPS redirects/HSTS as appropriate, and verify proxy access-log redaction before real-user release. Until then, keep this deployment restricted to an explicitly non-production environment.

### P1 — BLOCK — V8 credential migration is only safe for the seeded demo account

- **Location:** `doctor-wang-mock-server/src/main/resources/db/migration/V8__harden_user_credentials_and_jwt_sessions.sql:1-10`.
- **Issue / impact:** Only `demo@doctorwang.app` receives a BCrypt hash before `password_hash` becomes `NOT NULL` and plaintext `password` is dropped. A database with any other user cannot safely use this migration as written, and a failed/forced migration risks availability or credential-state loss.
- **Evidence:** Static SQL review. PostgreSQL/Testcontainers migration execution is still unavailable in this environment.
- **Recommendation:** Keep production rollout blocked pending an approved reset/backfill + backup/restore runbook for all non-demo accounts, and execute it in a Docker-capable/controlled PostgreSQL environment.

### P2 — Startup/readiness and runtime configuration evidence is incomplete

- **Location:** `AuthProperties.kt:5-10`; `JwtTokenService.kt:93-98`; `application.yml:31-36`.
- **Issue / impact:** Constructor validation now rejects invalid authentication properties before normal Spring serving, but no Spring-context readiness test proves deployment startup failure semantics, and the defensive runtime 503 path is not independently exercised.
- **Evidence:** `AuthProperties.kt` constructor guard and `JwtTokenServiceTest` startup tests; startup/readiness and runtime 503 are marked NOT RUN by QA.
- **Recommendation:** Validate secret presence/minimum entropy-compatible size, issuer, and audience at configuration binding/startup; expose a readiness failure and a non-secret-safe operational signal. Add a Spring-context/configuration test.

### P2 — JWT hardening has incomplete HTTP/filter-level regression evidence for revoked/expired sessions

- **Location:** `JwtTokenServiceTest.kt:12-43`; `AuthTokenFilter.kt:42-59`.
- **Issue / impact:** The HTTP/filter configuration-error path is now covered, but no test proves that an expired/revoked/mismatched session is rejected through the actual HTTP filter. This weakens evidence for the remaining trust-boundary controls.
- **Evidence:** The current `AuthTokenFilterTest` asserts fail-fast construction for invalid configuration. The suite still has no HTTP/filter test for runtime 503, expiry, revoked JTI, issuer/audience/JTI mismatch, token-hash mismatch, or user mismatch; PostgreSQL integration cannot run in this environment.
- **Recommendation:** Retain the verified 503 case and add filter/MockMvc tests for expired token, revoked JTI, issuer/audience/JTI mismatch, token hash/user mismatch, and absence of raw token in error/log fixtures; execute them and archive results.

### P2 — Production logging and Android release transport protections remain unverified

- **Location:** `app/src/debug/AndroidManifest.xml:4-7`; `app/src/main/res/xml/network_security_config.xml:1-4`; deploy proxy configuration.
- **Issue / impact:** Debug allows cleartext traffic, appropriate only if it cannot leak into a release variant. There is no release APK/network-security inspection, runtime proxy log redaction test, or deployment logging configuration evidence. Tokens and health data may still leak through infrastructure logs or an unintended cleartext build/configuration.
- **Evidence:** Debug-only manifest references a config whose base policy allows cleartext. No source-level BODY logger was found, but runtime/infrastructure behavior was not run.
- **Recommendation:** Add release-variant tests or artifact inspection proving cleartext is prohibited; test/redact Nginx and application access logs; prohibit HTTP user-configured server URLs in release builds.

## Checks and evidence

| Check | Result | Evidence |
| --- | --- | --- |
| Static focused source and test review | PASS | Paths and line references above. |
| Sensitive-logging repository search | PASS (source-only) | No production BODY/Authorization logging call found in reviewed paths. |
| `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --rerun-tasks` | PASS | `BUILD SUCCESSFUL`; covers startup configuration, TTL, JOSE header and password hashing unit tests. See `.ai/reports/authentication-startup-validation-qa.md`. |
| Authentication configuration failure through filter | PASS | `AuthTokenFilterTest.authentication configuration failure is returned as service unavailable` asserts HTTP 503 and `AUTH_CONFIGURATION_INVALID` for a short JWT secret; included in QA's serialized backend-unit PASS. |
| Final integration-test assertion re-review | PASS (static) | `PostgresIntegrationMatrixTest` now asserts Flyway 1–8 and the V8 schema contract for non-null `password_hash`, `token_hash varchar(64)`, non-null JTI and JTI uniqueness. It still has no executable legacy-session/revocation assertion. |
| PostgreSQL/Flyway integration matrix | BLOCKED | QA ran `bash ./gradlew --no-daemon postgresIntegrationTest --stacktrace`; Testcontainers cannot find a Docker environment and no Docker CLI/socket is available. |
| Android unit/UI/release security tests | NOT RUN | Repository has no Android test source evidence for these controls. |

## Required disposition

Do not mark AUTH-001 accepted or production-ready. The focused prior JWT findings (TTL, JOSE header, configuration response mapping) are **statically remediated**, subject to a clean test run. The P1 deployment and credential-migration gates above still require remediation and independent verification. After those actions, QA should run the Docker-capable migration matrix and Security Reviewer should perform a fresh release re-review.

## Final integration-assertion re-review

The current `PostgresIntegrationMatrixTest` was re-read after the engineer updated it. The revised first test correctly expects Flyway versions **1 through 8** and asserts the important V8 schema properties (`password_hash` non-null, `token_hash` length 64, JTI non-null and unique). This resolves QA's finding that the test was explicitly stale at V7.

It is still **not execution evidence**: Testcontainers cannot initialize here. It also does not exercise a database that contains a pre-V8 opaque session, nor does it insert/revoke a session and prove the filter rejects it. Those scenarios remain required in Docker-capable CI before the V8/session migration can be treated as verified.
