## Agent Receipt

Task ID: DEPLOY-001a
Agent: architect
Role: Architect（生产化阶段，用户授权后实施）
Date: 2026-08-14

### Status
ACCEPT（本地验证全绿；Docker 侧配置已就位但验证延 Docker）

### Summary
部署加固第一片（ADR-0009）：
1. 引入 `spring-boot-starter-actuator`，白名单暴露 `/actuator/health`（不暴露 env/beans 等敏感端点），`show-details: never`
2. 新增 `ActuatorHealthTest`（最小 actuator 自动配置上下文，排除 DataSource/Flyway/JPA）：`/actuator/health` → 200 + `{"status":"UP"}` 实测通过
3. compose backend 增加 healthcheck（curl /actuator/health），Dockerfile runtime 安装 curl
4. 后端全量 70 tests / 0 failures（JDK 21）+ bootJar + diff-check PASS

### Changed Files
- `doctor-wang-mock-server/build.gradle.kts` — +spring-boot-starter-actuator
- `doctor-wang-mock-server/src/main/resources/application.yml` — management.endpoints（仅 health）
- `doctor-wang-mock-server/Dockerfile` — runtime 安装 curl（healthcheck 依赖）
- `deploy/docker-compose.yml` — backend healthcheck
- 测试：`config/ActuatorHealthTestConfig.kt`、`controller/ActuatorHealthTest.kt` — 新增
- `.ai/decisions/ADR-0009-deployment-hardening.md` — 新增 ADR（001a 已验收，001b proposed）

### Tests Run
```
JAVA_HOME=<ws>/.jdk21/... GRADLE_USER_HOME=<ws>/.gradle-home \
<cached>/gradle-8.10.2/bin/gradle test bootJar --rerun-tasks --no-daemon \
-Dkotlin.compiler.execution.strategy=in-process
```

### Test Result
PASS — BUILD SUCCESSFUL；后端 **70 tests / 0 failures / 0 errors**（含 ActuatorHealthTest）；bootJar SUCCESSFUL；`git diff --check` PASS。全项目累计 Android 15 + backend 70 + AI 86 = **171 tests PASS**。

### Evidence
- `ActuatorHealthTest`：Spring 上下文（actuator 自动配置，db 指示器禁用）实测 `/actuator/health` → 200 UP
- compose healthcheck + Dockerfile curl 已配置（Docker 环境验证 NOT RUN）

### Known Risks
- compose healthcheck/镜像未在真实 Docker 环境验证 — P1，需 Docker
- actuator 仅暴露 health；metrics/readiness 细化（DB 指示器）随 DEPLOY-001b 处理 — P3

### Blockers
- 无（本地验证完成；Docker/生产验证为环境延期项）

### Unfinished Items
- DEPLOY-001b：证书自动供给、监控告警、HA、ClamAV 编排、CI — ADR-0009 已出方案，实施需 Docker/生产环境
- actuator metrics 暴露与 DB readiness 探针 — 随 001b

### Recommended Next Action
DEPLOY-001b 在 Docker/生产环境实施（方案见 ADR-0009）；或提供 Docker/API keys/真机补跑全部延期验证。
