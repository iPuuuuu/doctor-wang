---
name: authentication-jwt-claims-qa
description: AUTH-001 JWT verifier claims and malformed-token QA
status: VERIFIED
---

# AUTH-001 JWT Claims QA

## Scope and result

The direct verifier boundary slice is **VERIFIED** locally. Tests cover expired `exp`, wrong issuer/audience, missing `sub`/`uid`/`jti`, malformed Base64 payload/header, JOSE allow-list, TTL/configuration boundaries, and password hashing. This does not lift the overall AUTH-001 release block.

## Evidence

| Check | Exact command | Result |
|---|---|---|
| Backend full unit suite | `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --rerun-tasks` | PASS, `BUILD SUCCESSFUL in 6s` |
| Whitespace check | `git diff --check` | PASS |
| PostgreSQL/Flyway/Hibernate | `bash ./gradlew --no-daemon postgresIntegrationTest --stacktrace` | BLOCKED: no Docker runtime/socket |
| HTTP filter session/revocation | No executable persistence/filter matrix in this slice | NOT RUN |

The payload parser catch was hardened to convert any JSON/Base64 parsing exception into the generic authentication failure instead of allowing a Jackson parse exception to escape.

## Remaining risks

- No end-to-end filter test for expired/revoked/mismatched sessions or user isolation.
- No Docker-capable V8 migration execution.
- P1 TLS and non-demo credential migration blockers remain.
