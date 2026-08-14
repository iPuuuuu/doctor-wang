## Handoff
- Task ID: AUTH-001
- Agent: Quality / Security Reviewer (autopilot filter-fix independent review)
- Status: READY_FOR_REVIEW
- Scope changed: Independent read-only review of the bounded `AuthTokenFilter.shouldNotFilter` public-route normalization repair. No business/runtime source, API, schema, deployment, or test source was modified by this review.
- Files changed:
  - `.ai/handoffs/AUTH-001/quality-autopilot-filter-fix.md`
- Decisions: The route normalization repair resolves the immediate MockMvc login regression and preserves exact path matching. The sub-slice is **REWORK** because public-route bypass remains independent of HTTP method; restrict the three public endpoints to their declared safe methods before treating this authorization-boundary repair as accepted. This is a sub-slice verdict only and does not supersede AUTH-001's existing overall release `BLOCK`.
- Tests and exact commands:
  - `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests 'com.ipuuuuu.doctorwang.mock.controller.AuthHttpContractTest' --tests 'com.ipuuuuu.doctorwang.mock.config.AuthTokenFilterTest'`
  - `cd /Users/a1234/code/doctor-wang/doctor-wang-mock-server && git diff --check`
  - Static review of `src/main/kotlin/com/ipuuuuu/doctorwang/mock/config/AuthTokenFilter.kt`, `src/main/kotlin/com/ipuuuuu/doctorwang/mock/controller/AuthController.kt`, `src/main/kotlin/com/ipuuuuu/doctorwang/mock/controller/HealthController.kt`, and their focused tests.
- Results and evidence:
  - **PASS** — focused Gradle test command completed `BUILD SUCCESSFUL in 7s`; it exercised `AuthHttpContractTest` and `AuthTokenFilterTest`, including unauthenticated `POST /api/login` reaching the controller.
  - **PASS** — `git diff --check` completed without whitespace errors.
  - **PASS** — application-relative derivation removes the actual `contextPath` from `requestURI`; an empty post-strip value is normalized to `/`. This covers the relevant context-path representation without using prefix allow-list matching.
  - **PASS** — allow-list is exact: only `/api/login`, `/healthz`, and `/` bypass; `/api/login/`, `/api/login/anything`, and similarly prefixed routes do not match. Query parameters are absent from servlet `requestURI`, so they cannot alter this route decision.
  - **PASS** — protected requests still verify the JWT and persisted JTI/session state before setting `AuthContext`; failure responses contain fixed public error codes/messages and a trace ID, rather than the bearer token, JWT claims, stack trace, or persistence detail.
  - **P2 / REWORK** — `AuthTokenFilter.kt:31` exempts every HTTP method for each public path. Current controller mappings are `POST /api/login` and `GET /`, `GET /healthz`, so unsupported methods currently arrive at Spring and should be rejected as 405. Nevertheless, the authentication boundary itself no longer enforces that contract: a future `GET`, `PUT`, `DELETE`, or another state-changing handler added at any of these exact paths would automatically be unauthenticated. Make the exemption method-aware (`POST` only for `/api/login`; `GET`/optionally `HEAD` only for health endpoints), and add regression tests showing disallowed methods do not bypass the filter.
  - **P3 / NOT RUN** — focused tests do not explicitly construct a non-empty `contextPath`, a trailing-slash route, or an allow-listed route using a disallowed HTTP method. The implementation is readable and exact-path behavior is clear from source, but executable coverage for these boundary inputs should accompany the P2 repair.
  - **P3 / NOT RUN** — no live reverse-proxy/container servlet deployment test was available in this review. The code's use of servlet-provided `contextPath` is sound for the normal servlet contract, but proxy deployment verification remains outside this no-Docker slice.
- Risks / privacy / quality concerns:
  - P0: none found in this bounded diff.
  - P1: none newly introduced by this bounded route-normalization change. Existing AUTH-001 P1 release gates (HTTP-only deployment and demo-only credential migration) remain unchanged and continue to block the parent task.
  - P2: method-agnostic public-route bypass described above. It is a maintainability/authorization-boundary risk, not evidence of a currently mapped unauthenticated state-changing endpoint.
  - Sensitive error behavior is appropriate for the inspected filter paths: malformed/invalid credentials fail with generic 401, configuration failure is a generic 503, and neither response serializes bearer material or JWT/session internals.
- Open questions:
  - Product/operations should explicitly decide whether unauthenticated health probes require `HEAD` in addition to `GET`; do not silently permit every method.
  - After method restrictions and tests are added, re-run this independent review. Container/proxy behavior and the parent AUTH-001 release gates still require their separately recorded evidence.
- Next owner: Backend Engineer for the narrow method-aware allow-list repair and tests; then QA and Quality / Security Reviewer for re-check.

## Review Conclusion

**REWORK** — The context-path/public-path repair is correct for the observed login regression, but the filter's public-route authorization bypass must be constrained by the explicitly supported HTTP methods and covered with boundary tests. This conclusion applies only to the AUTH-001 filter-fix sub-slice; AUTH-001 remains subject to its already-recorded broader `BLOCK` gates.
