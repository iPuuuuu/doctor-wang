---
name: authentication-autopilot-security-review
description: AUTH-001 continuous-autopilot security re-review of deployment, migration, authentication and logging boundaries
metadata:
  type: security-report
status: BLOCK
date: 2026-08-10
---

# AUTH-001 Security Reviewer — Autopilot Re-review

## Scope and conclusion

独立只读复核当前工作树的部署 TLS/cleartext、V8 凭据迁移、JWT/session 认证边界以及日志暴露。未修改业务代码、部署代码或 migration。结论为 **BLOCK**：两项既有 P1 发布阻断仍存在，并发现部署默认数据库凭据和 release 可配置明文 URL 的额外风险。后端 focused unit tests 通过不能替代 PostgreSQL/Flyway、Android release 和受控部署证据。

## Findings

### P1 — BLOCK — Public deployment has no TLS enforcement

- **Location/evidence:** `deploy/docker-compose.yml:39-48` publishes only `80:80`; `deploy/nginx/default.conf:1-15` has only `listen 80` and no redirect/443 certificate; `deploy/README.md:84-94` explicitly says HTTPS is not configured.
- **Impact:** Bearer JWTs and health data are sent through a plaintext public entry point and can be intercepted or modified. The config must remain non-production until a TLS-terminating boundary is documented and verified.
- **Recommendation:** Add/verify 443 TLS (or an explicitly controlled external terminator), redirect HTTP, configure HSTS where appropriate, and test proxy/access-log redaction.

### P1 — BLOCK — V8 is a demo-only credential migration, not a general-user production migration

- **Location/evidence:** `V8__harden_user_credentials_and_jwt_sessions.sql:1-10` backfills one fixed BCrypt value only for `demo@doctorwang.app`, then sets `password_hash NOT NULL` and drops plaintext `password`. `V2__seed_demo_user.sql:1-2` contains the historical demo password `123456`.
- **Impact:** Any non-demo account is left NULL and migration fails at the NOT NULL operation (or risks credential-state loss if forced). The fixed demo secret must not be used for real users. Legacy sessions are intentionally deleted at `V8:15`; this requires approved re-login, backup and rollback evidence.
- **Recommendation:** Keep release blocked until a separately approved reset/backfill runbook handles every non-demo account, with backup/restore rehearsal and production-secret separation. Never apply this migration unchanged to a real-user database.

### P2 — BLOCK/REWORK — Deployment defaults include a weak database password

- **Location/evidence:** `deploy/docker-compose.yml:7-9,27-29` defaults `POSTGRES_PASSWORD` to `doctor_wang`; `doctor-wang-mock-server/src/main/resources/application.yml:15-17` has the same development fallback.
- **Impact:** A deployment started without an explicit `.env` exposes a guessable database credential. This violates production secret handling even though `APP_AUTH_JWT_SECRET` is required.
- **Recommendation:** Remove production fallbacks or fail fast when `SPRING_DATASOURCE_PASSWORD`/`POSTGRES_PASSWORD` are absent; ensure examples are clearly non-production and deployment checks reject defaults.

### P2 — Android release transport boundary is not enforced at the URL input boundary

- **Location/evidence:** `SessionManager.saveServerUrl` (`app/.../SessionManager.kt:59-63,81-85`) accepts any scheme; Settings UI accepts arbitrary text (`SettingsScreen.kt:68-73`). Release build injects an HTTPS default (`app/build.gradle.kts:41-53`), but a user can persist an `http://` URL. Debug manifest/config explicitly permits cleartext (`app/src/debug/AndroidManifest.xml:4-6`, `app/src/debug/res/xml/network_security_config.xml:1-4`).
- **Impact:** Release default is HTTPS, but a misconfigured/custom endpoint can send credentials over cleartext (or fail unpredictably depending on Android network policy). This is not release-security evidence.
- **Recommendation:** Validate release server URLs as HTTPS (allow HTTP only in debug), add release APK/network-security inspection and URL-policy tests, and document the debug-only exception.

### P2 — No server-side logout/revoke endpoint

- **Location/evidence:** `AuthController.kt:16-20` exposes login only; `UserSessionRepository` has lookup by JTI but no revoke operation; Android logout only clears local DataStore (`RemoteDoctorWangRepository.kt` logout path).
- **Impact:** A stolen JWT remains accepted until expiry despite the `revoked_at` field. This is a bounded-TTL limitation, not active revocation.
- **Recommendation:** Add an authenticated, user-scoped revoke endpoint/service or record explicit risk acceptance and incident response; test post-revoke 401 behavior through the HTTP layer.

### P3 — Logging evidence is source-only

- **Evidence:** Repository search found no production `HttpLoggingInterceptor`, `Level.BODY`, Authorization/Bearer logging, `println`, or `Log.*` calls in reviewed Android/backend paths; `NetworkProvider` only adds the header and clears session on 401 (`NetworkProvider.kt:22-38`). No runtime proxy/access-log inspection was possible.
- **Disposition:** PASS for source search; NOT RUN for release artifact and infrastructure logs. Do not claim end-to-end redaction until those checks run.

## Checks

| Check | Result | Evidence |
| --- | --- | --- |
| Focused backend auth tests | PASS | `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests '*AuthTokenFilterTest' --tests '*JwtTokenServiceTest' --tests '*BackendServiceTest'` → `BUILD SUCCESSFUL` (6s) |
| Sensitive logging source search | PASS (source-only) | `rg -n -i 'HttpLoggingInterceptor|Level\\.BODY|Authorization|Bearer|println\\(|Log\\.|cleartext' app doctor-wang-mock-server deploy` |
| Docker/Compose validation | BLOCKED | `docker version` → `command not found: docker`; cannot run compose config or Testcontainers |
| PostgreSQL/Flyway V8 matrix | BLOCKED | Existing QA evidence: Testcontainers cannot find Docker environment |
| Android release APK/network policy | NOT RUN | No configured emulator/APK inspection in this environment |
| Runtime proxy/access-log redaction | NOT RUN | No deployed environment available |

## Required disposition and next owner

**BLOCK.** No P0 found. Backend/Deployment owner must address TLS and weak database defaults; Backend Lead must provide a safe non-demo credential migration/reset procedure and decide server-side revoke semantics. QA owns Docker-capable PostgreSQL and Android release evidence. Architect owns risk acceptance and final gate decision. Re-review is required after P1 remediation and live migration evidence.
