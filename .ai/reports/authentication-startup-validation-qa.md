# AUTH-001 Startup Authentication Validation QA

Date: 2026-08-09
Owner: QA Engineer
Scope: independent verification of the startup validation slice for JWT authentication configuration.

## Conclusion

`VERIFIED` for the bounded startup-validation slice. The implementation and explicit constructor tests pass. AUTH-001 overall remains `BLOCKED` because Docker/PostgreSQL, TLS, credential migration, and broader HTTP/session evidence remain unresolved.

## Checks

| Check | Exact command / evidence | Result |
|---|---|---|
| Backend regression | `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --rerun-tasks` | **PASS** — Gradle `BUILD SUCCESSFUL in 6s`; all configured tests executed. Kotlin compiler emitted only pre-existing unchecked-cast warnings in `JwtTokenService.kt`. |
| Whitespace / patch integrity | `git diff --check` | **PASS** — no whitespace errors. |
| Short JWT secret | `JwtTokenServiceTest.invalid jwt security configuration is rejected at startup`; `AuthProperties(jwtSecret = "too-short-secret")` | **PASS** — `IllegalArgumentException` asserted. |
| Empty JWT secret | `AuthProperties(jwtSecret = "")` | **PASS** |
| Empty issuer | `AuthProperties(jwtSecret = SECRET, jwtIssuer = "")` | **PASS** |
| Empty audience | `AuthProperties(jwtSecret = SECRET, jwtAudience = "")` | **PASS** |
| TTL lower/upper invalid boundaries | `jwtTtlSeconds = 0` and `86_401` are asserted to throw | **PASS** |
| TTL valid boundaries | `JwtTokenServiceTest.ttl endpoints are accepted` | **PASS** — both 1 and 86,400 are accepted. |
| Protected-filter 503 for invalid configuration | Existing `AuthTokenFilterTest` now only asserts startup rejection; it cannot construct `JwtTokenService` with an invalid secret because `AuthProperties` fails first | **NOT RUN** — the runtime `503 AUTH_CONFIGURATION_INVALID` branch remains source-present but requires a dedicated injected/mocked service test. |
| Integration migration | PostgreSQL/Testcontainers matrix | **BLOCKED** — Docker/Testcontainers environment is unavailable in this workspace; this remains the AUTH-001 release blocker. |

## Reviewed implementation

- `doctor-wang-mock-server/src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/AuthProperties.kt` performs constructor-time validation: secret length >= 32, non-blank/no-whitespace/no-control issuer and audience (max 256), and TTL 1..86,400.
- `doctor-wang-mock-server/src/test/kotlin/com/ipuuuuu/doctorwang/mock/service/JwtTokenServiceTest.kt` covers short-secret, whitespace issuer/audience, and invalid TTL cases.

## Risks and required follow-up

1. Add explicit tests for `jwtSecret = ""`, `jwtIssuer = ""`, and `jwtAudience = ""`.
2. Add valid endpoint tests for TTL 1 and 86,400 (and, ideally, verify issued token expiry semantics at those values).
3. Re-run this QA after the tests are added; do not mark `VERIFIED` based only on constructor implementation inspection.
4. Keep PostgreSQL/Testcontainers integration marked **BLOCKED** until a Docker-capable environment executes the matrix.
5. Previous hardening reports that called the short-secret filter 503 test `PASS` are stale after the startup fail-fast change; they require correction or an explicit historical-evidence qualifier.

No business implementation files were modified by QA.
