---
name: authentication-test-plan
description: AUTH-001 登录/JWT/凭据迁移测试方案与当前证据
metadata:
  type: qa-report
status: BLOCKED
---

# AUTH-001 Test Plan

## 范围

- `POST /api/login` 的输入、成功和统一失败响应；
- JWT signature/issuer/audience/expiry/JTI/session hash 校验；
- BCrypt 凭据验证和 V8 Flyway migration；
- Android 登录输入、loading、401 清 session 和导航；
- 用户隔离、日志脱敏和 Compose/Repository/ViewModel 回归。

## 当前证据

| 检查 | 命令 | 结果 |
|---|---|---|
| Backend unit/contract | `cd doctor-wang-mock-server && bash ./gradlew test --rerun-tasks` | PASS (with compile warnings) |
| Backend login-path unit | `cd doctor-wang-mock-server && bash ./gradlew --no-daemon test --tests '*BackendServiceTest'` | PASS |
| Android Gradle test | `bash ./gradlew test` | PASS，当前无 Android test source（NO-SOURCE） |
| PostgreSQL/Flyway matrix | `cd doctor-wang-mock-server && bash ./gradlew postgresIntegrationTest` | BLOCKED：Testcontainers 无可用 Docker client/socket，测试体未执行 |
| Diff whitespace | `git diff --check` | PASS |

## QA 执行记录（2026-08-09）

| 检查 | 命令 | 结果 | 证据 |
|---|---|---|---|
| JWT、密码哈希及后端回归 | `cd doctor-wang-mock-server && bash ./gradlew test` | PASS | `doctor-wang-mock-server/build/reports/tests/test/index.html`；Gradle `BUILD SUCCESSFUL` |
| PostgreSQL/Testcontainers + Flyway V8 | `cd doctor-wang-mock-server && bash ./gradlew postgresIntegrationTest --stacktrace` | BLOCKED | `doctor-wang-mock-server/build/test-results/postgresIntegrationTest/TEST-com.ipuuuuu.doctorwang.mock.integration.PostgresIntegrationMatrixTest.xml`：`Could not find a valid Docker environment`; `/var/run/docker.sock` 不存在 |
| Diff whitespace | `git diff --check` | PASS | 命令无输出、退出码 0 |

### 场景矩阵状态

- JWT 签发/身份字段、篡改、密码正确/错误匹配：PASS（现有 `JwtTokenServiceTest`、`PasswordHasherTest`）。登录接口正确凭据及错误账号/密码的 HTTP 契约：NOT RUN（无 controller/MockMvc 测试；代码检查不等同于执行证据）。
- 过期 token、错误 issuer/audience、缺少 JTI、撤销 session：PASS（`JwtTokenServiceTest` 已覆盖 verifier expiry/issuer/audience/missing claims；`AuthTokenFilterTest` 已覆盖 verifier expired-token 401、revoked persisted session 401、expired persisted session 401、config error 503、valid session pass-through）。真实 HTTP controller/MockMvc 契约和 Docker/PostgreSQL 集成仍分别记为 NOT RUN / BLOCKED。
- 重复请求和用户隔离：BLOCKED（PostgreSQL 矩阵未能启动；现有报告幂等测试与 namespace 单测不覆盖认证 filter）。
- 未登录/失效 token/401 响应：NOT RUN（无 HTTP 契约或 MockMvc 测试；Android 401 清理亦无实际测试 source）。
- Flyway 空库、存量库、备份恢复及 demo 登录：BLOCKED（Docker/PostgreSQL 不可用，未执行真实迁移）。
- 超时、断网、重试、日志敏感信息：NOT RUN（本 AUTH-001 测试套件未提供自动化证据，需后续专项测试）。

## 必测场景

- 正确凭据、错误账号、错误密码、空输入、重复提交；
- token 篡改、过期、错误 issuer/audience、无 JTI、撤销 session；
- 用户 A 访问用户 B 的 profile/chat/report；
- migration 空库、存量库、备份恢复和 demo 登录；
- provider/数据库不可用、网络超时、Android 401 和本地 session 清理；
- 密码、JWT、报告原文和健康数据不出现在日志。

## 结论

本地编译和单元测试通过，但在 PostgreSQL/Testcontainers 集成矩阵、迁移后的 Hibernate schema validation 和 Android 实际 UI 测试完成前不得解除发布阻断。V8 已在工作树中修正旧 opaque session 失效与 `token_hash` 列宽契约，仍需真实 PostgreSQL 复核。
