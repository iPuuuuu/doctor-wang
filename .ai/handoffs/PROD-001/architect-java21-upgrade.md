## Agent Receipt

Task ID: PROD-001
Agent: architect
Role: Architect（生产化阶段首个任务）
Date: 2026-08-14

### Status
ACCEPT（构建验证完成；镜像启动验证依赖 Docker 延期）

### Summary
后端运行时升级 Java 21 LTS（ADR-0007）：
1. `doctor-wang-mock-server/build.gradle.kts` toolchain 17 → 21（Kotlin 2.0.21 默认对齐 jvmTarget）
2. `Dockerfile` 双阶段 `eclipse-temurin:17-jdk/jre` → `21-jdk/jre`
3. `README.md` 部署章节 Java 17 → 21
4. 验证：先 JDK 21 跑 Gradle + toolchain 17 基线 PASS（隔离工具链兼容性），再 toolchain 21 `clean test bootJar` 全绿
5. Android 根构建为独立 Gradle build（仅 :app，保持 Java 17 编译目标），不受影响

### Changed Files
- `doctor-wang-mock-server/build.gradle.kts` — toolchain `JavaLanguageVersion.of(21)`
- `doctor-wang-mock-server/Dockerfile` — temurin 21-jdk（build）/ 21-jre（runtime）
- `doctor-wang-mock-server/README.md` — 部署目标 Java 21
- `.ai/decisions/ADR-0007-java21-upgrade.md` — 新增 ADR（accepted-by-architect）
- `.gitignore` — `.jdk21/`（工作区自带 JDK 21）

### Tests Run
```
JAVA_HOME=<ws>/.jdk21/jdk-21.0.12+8/Contents/Home \
GRADLE_USER_HOME=<ws>/.gradle-home <cached>/gradle-8.10.2/bin/gradle \
clean test bootJar --rerun-tasks --no-daemon -Dkotlin.compiler.execution.strategy=in-process
```

### Test Result
PASS — BUILD SUCCESSFUL in 20s；**56 tests / 0 failures / 0 errors**（JDK 21 + toolchain 21）；bootJar 产物字节码 major version 65（Java 21）实测；`git diff --check` PASS。

### Evidence
- 基线（JDK 21 + toolchain 17）：BUILD SUCCESSFUL（先验证 Gradle/Spring 与 JDK 21 兼容）
- 升级后：`clean test bootJar` BUILD SUCCESSFUL；测试报告 11 套件 56 tests 全绿
- 字节码：`cafebabe 00 00 00 41`（major 65 = Java 21）
- ADR-0007：`.ai/decisions/ADR-0007-java21-upgrade.md`

### Known Risks
- 21-jre 镜像启动/健康检查未验证（无 Docker）— P1，需 Docker 环境补跑
- 虚拟线程等新特性未启用（本次仅运行时升级，不引入新用法）— P3

### Blockers
- 无（构建验证完成；Docker 镜像验证为环境延期项）

### Unfinished Items
- `eclipse-temurin:21-jre` 容器启动 + 健康检查 smoke — 需 Docker 环境
- DEPLOY-001 / SEC-001 尚未开工 — 待 Architect 方案与用户授权

### Recommended Next Action
DEPLOY-001（部署加固）与 SEC-001（上传安全：NoopVirusScanner 替换）需先形成 ADR 方案（含替代方案/迁移/回滚/验收标准）并经用户授权后实施；或提供 Docker/CI 补跑 PROD-001 镜像验证。
