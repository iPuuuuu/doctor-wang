## Handoff
- Task ID: AUTH-001
- Agent: Security Reviewer (JWT hardening focused re-review)
- Status: BLOCKED
- Scope changed: None. Read-only review of TTL, JOSE header validation, JWT configuration-error handling, and bearer/logging exposure. Documentation evidence only.
- Files changed: `.ai/reports/authentication-hardening-security.md`; `.ai/handoffs/AUTH-001/security-hardening.md`.
- Decisions: The current source statically remediates the targeted TTL, JOSE-header, and 503 configuration-response findings. QA has a PASS backend-unit result, but AUTH-001 remains release-BLOCKED because P1 TLS and general-user credential-migration controls remain unresolved and live PostgreSQL evidence is unavailable.
- Tests and exact commands:
  - `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --rerun-tasks` (QA evidence) — PASS, `BUILD SUCCESSFUL`; this covers direct JWT TTL/JOSE tests and `AuthTokenFilterTest`, which asserts the short-secret filter path returns HTTP 503 with `AUTH_CONFIGURATION_INVALID`.
  - Final static re-review of `PostgresIntegrationMatrixTest` — PASS: it now asserts Flyway 1–8 plus V8 schema properties for `password_hash`, `token_hash(64)`, JTI non-null and JTI uniqueness. Its Testcontainers execution remains BLOCKED and it does not prove legacy-session deletion or filter-level revocation rejection.
  - Focused static reads: `JwtTokenService.kt`, `AuthTokenFilter.kt`, `AuthProperties.kt`, `application.yml`, `deploy/docker-compose.yml`, Android network/manifests, and `JwtTokenServiceTest.kt` — PASS as source inspection.
  - Source search for `HttpLoggingInterceptor`, `Level.BODY`, `Authorization`, `Bearer`, password, and logging calls — PASS for absence of production body/bearer logging; runtime proxy/logging verification remains NOT RUN.
  - `bash ./gradlew postgresIntegrationTest` — BLOCKED according to current QA evidence: Testcontainers cannot find Docker runtime/socket.
- Results and evidence:
  - TTL constructor guard accepts only 1–86,400 seconds; direct unit coverage is present but this reviewer could not independently execute it.
  - JWT verification decodes and requires `alg=HS256`/`typ=JWT` before signature and claims processing.
  - Auth filter returns 503 with safe `AUTH_CONFIGURATION_INVALID` response for a short-secret configuration error instead of collapsing it to 401.
  - No source-level BODY/bearer logging was found; Android clears session on 401.
  - Full findings, P0–P3 disposition, and exact evidence are in `.ai/reports/authentication-hardening-security.md`.
- Risks / privacy / quality concerns:
  - P1: public deployment is still HTTP-only, exposing JWT and health data in transit.
  - P1: V8 hashes only the demo account before non-null/drop-column operations; it is not an approved safe path for databases with real users.
  - P2: secret/issuer/audience are not all validated at startup; configuration is only detected when signature logic is invoked.
  - P2: The short-secret HTTP 503 filter case is now PASS; revocation/expiry/claim-mismatch filter tests, Android release transport checks, proxy log-redaction tests, and live PostgreSQL/Flyway evidence remain incomplete.
- Open questions:
  - Which TLS-terminating production deployment and Nginx logging-redaction configuration will be approved and verified?
  - What approved reset/backfill plus backup/restore runbook handles every non-demo user before V8?
  - Can QA provide a clean focused Gradle run after the active Gradle process/cache lock releases, and a Docker-capable PostgreSQL integration run?
- Next owner: Architect to preserve AUTH-001 BLOCK; Backend/Deployment owners to remediate P1/P2 gaps; QA in Docker-capable CI; Security Reviewer for final independent re-review.

## Security conclusion

**BLOCK** — no P0 was found. QA's serialized backend-unit run now supplies PASS evidence for the focused JWT/filter tests, but outstanding P1 transport/credential-migration controls prohibit AUTH-001 release acceptance.

## Corrective addendum — filter configuration path

`AuthTokenFilterTest.kt` was subsequently discovered and reviewed. Its authenticated mock request uses a short JWT secret and asserts HTTP `503` with the safe `AUTH_CONFIGURATION_INVALID` code. QA included this test in its serialized backend-unit PASS. The earlier statement that this specific HTTP/filter configuration path lacked automated evidence is corrected to **PASS**. This does not alter the overall **BLOCK** conclusion or the remaining P1/P2 findings.
