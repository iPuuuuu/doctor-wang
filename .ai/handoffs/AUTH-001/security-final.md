## Handoff
- Task ID: AUTH-001
- Agent: Security Reviewer (independent final pass)
- Status: BLOCKED
- Scope changed: None; read-only review of ADR-0003, current authentication implementation and V8 migration, logout API/filter/client, Android release transport configuration, and Nginx/Compose secret handling.
- Files changed: `.ai/handoffs/AUTH-001/security-final.md` only (review evidence).
- Decisions: ADR-0003 decisions are implemented in the current working tree at source/config level: portable `pgcrypto` BCrypt migration, server-side user-scoped logout, bounded JWT TTL, strict HS256/JWT header checks, and Nginx TLS termination with HTTP redirect. Release cannot be marked verified until the required PostgreSQL/Testcontainers and deployment/Android smoke evidence is produced.
- Tests and exact commands:
  - `git diff --check` — PASS.
  - `cd doctor-wang-mock-server && bash ./gradlew test` — NOT RUN/blocked by local environment: Gradle wrapper could not open `/Users/a1234/.gradle/wrapper/dists/gradle-8.10.2-bin/...zip.lck` (`Operation not permitted`); no test result can be claimed from this pass.
  - `cd doctor-wang-mock-server && bash ./gradlew postgresIntegrationTest` — NOT RUN; Docker/Testcontainers runtime is unavailable (`docker` command absent and `/var/run/docker.sock` absent). Prior unit evidence in QA handoff is not re-attributed as independently executed here.
  - `docker compose --env-file deploy/.env config` / `nginx -t` / HTTPS and HTTP redirect smoke — NOT RUN; Docker CLI and a real certificate/endpoint are unavailable. Static source inspection completed.
  - Android release APK cleartext and HTTPS endpoint inspection — NOT RUN; no release APK was built in this environment.
- Results and evidence:
  - `doctor-wang-mock-server/src/main/resources/db/migration/V8__harden_user_credentials_and_jwt_sessions.sql` enables `pgcrypto`, hashes every non-null legacy password with `crypt(... gen_salt('bf', 12))`, sets `password_hash` non-null, drops plaintext `password`, deletes legacy sessions, and adds 64-character token hash/JTI/revocation schema. This matches ADR-0003; execution against PostgreSQL remains unverified.
  - `AuthController` exposes `POST /api/logout`; `BackendService.logout` revokes only the current `AuthContext` JTI and user ID with an idempotent update. `AuthTokenFilter` validates JWT/session binding before permitting logout and permits repeated logout only for that same revoked session. Android `RemoteDoctorWangRepository.logout` attempts server revoke and always clears local session in `finally`.
  - `AuthProperties` enforces a 32-character minimum secret, valid issuer/audience, and TTL 1..86,400 seconds. `JwtTokenService.verify` explicitly requires JOSE `alg=HS256`, `typ=JWT`, signature, issuer, audience, subject, uid, JTI and non-expired `exp`; configuration errors are returned as 503 by the filter.
  - Compose publishes Nginx on 80/443 only; backend exposes port 8080 internally. Nginx redirects all HTTP to HTTPS, enables TLS 1.2/1.3, mounts certs read-only from required `${TLS_CERTS_DIR}`, and Compose requires database password and JWT secret environment variables. No secret/certificate material is present in the reviewed files.
  - Android release URL is injected by Gradle property/environment variable and defaults to an HTTPS placeholder; debug-only manifest permits cleartext. Release manifest does not enable cleartext and no BODY logger was found in the reviewed network provider.
- Risks / privacy / quality concerns:
  - **P1 BLOCK (evidence gate):** PostgreSQL/Flyway/Hibernate migration and real TLS endpoint have not been executed in a Docker-capable environment. ADR-0003 explicitly requires those artifacts before release.
  - **P2:** Android release build/manifest and proxy access-log redaction were not independently exercised; retain as verification work, not as a claim of failure. Do not publish a release until HTTPS URL injection and cleartext exclusion are checked on the built artifact.
  - **P2:** V8 irreversibly removes plaintext passwords and invalidates all sessions. Production application requires the ADR maintenance-window backup/restore and preflight runbook; no evidence shows it has been run against a shared/production database. If V8 has already been applied outside this repository, stop and create a follow-up migration/runbook rather than reapplying it.
  - **P3:** Nginx uses `server_name _` and redirects using `$host`; validate the intended hostname/certificate and upstream proxy access-log policy in deployment smoke tests.
- Open questions: None requiring product decision; the remaining work is evidence collection in a Docker-capable, certificate-bearing controlled environment.
- Next owner: QA/Deployment owner to run PostgreSQL/Testcontainers, `docker compose config`, `nginx -t`, and HTTPS/HTTP smoke; Android owner to build and inspect release transport; Security Reviewer to re-review evidence; Architect to decide final gate.

## Security conclusion

`BLOCK` — no new P1 implementation defect was found in the current tree and the prior logout/TLS/TTL/parser findings are addressed at source/config level, but mandatory integration and deployment/release evidence is unavailable. Do not describe AUTH-001 as ACCEPT until those checks pass and the handoff is updated to VERIFIED.
