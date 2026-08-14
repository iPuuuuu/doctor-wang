---
id: ADR-0007
title: Upgrade backend runtime to Java 21 LTS with phased compatibility gate
status: accepted-by-architect
date: 2026-08-14
owners: [architect-agent]
---

# ADR-0007：后端 Java 21 升级方案

## 背景

- 目标架构（`AGENTS.md` / `context.md`）：Spring Boot 3 + **Java 21**。
- 现状：`doctor-wang-mock-server` 使用 Kotlin/JVM 17 toolchain + Spring Boot 3.3.3 + Kotlin 2.0.21；Dockerfile 使用 `eclipse-temurin:17-jdk`（build）与 `17-jre`（runtime）。
- 兼容性事实：Java 17 EOL 2029-09，Java 21 LTS 支持至 2031-09；Spring Boot 3.x 官方支持 Java 17–23；Spring Framework 6.1 支持虚拟线程；Kotlin 2.0.21 支持 JVM target 21（Kotlin 2.0+ 要求）。
- 收益：虚拟线程（报告处理 worker / AI 回调等高并发路径）、ZGC 低延迟 GC、records/pattern matching、更长的 LTS 支持窗口。

## 决策

1. **JVM toolchain 17 → 21**：`doctor-wang-mock-server/build.gradle.kts` 中 `JavaLanguageVersion.of(17)` 改为 `of(21)`，`jvmTarget`/`kotlinOptions` 同步为 21。
2. **Dockerfile 双阶段镜像升级**：`eclipse-temurin:17-jdk` → `21-jdk`（build 阶段），`17-jre` → `21-jre`（runtime 阶段）。
3. **本 ADR 仅做运行时升级**：Spring Boot 3.3.3 与 Kotlin 2.0.21 版本保持不变；框架大版本升级另行 ADR，不混入本次变更。
4. **兼容性门禁（硬性）**：JDK 21 上必须跑通 `gradle test --rerun-tasks`（全部 56 后端测试）+ `bootJar` 构建；Hibernate `ddl-auto: validate` 与 Flyway migration 顺序不得受影响；Android/AI 侧无变更。

## 备选方案与取舍

- **维持 Java 17（status quo）**：无需改动，但 2029 年 EOL 前仍须升级；虚拟线程等能力不可用；技术债持续累积 — 不选择。
- **直接升 Spring Boot 4 / Java 25**：运行时与框架升级耦合，风险面过大且无当前收益 — 不选择，框架升级留待独立 ADR。
- **Gradle toolchain auto-provisioning（foojay-resolver 插件）**：可自动下载 JDK 21，但引入新插件与网络依赖，且 CI/本地显式安装 JDK 更可审计 — 不选择，采用显式 toolchain 21 + 环境提供 JDK 21。

## 迁移与回滚

- **迁移**：① 单文件改动 `build.gradle.kts`（toolchain + jvmTarget）→ ② 本地 JDK 21 执行 `gradle test --rerun-tasks` 验证 → ③ Dockerfile 镜像版本升级 → ④ 部署验证（健康检查 + smoke）。
- **回滚**：toolchain 改回 17 + Dockerfile 回 17-jre。纯构建/镜像层面改动，无 schema、数据或 API 契约影响，单行级回滚。
- 不做：修改既有 Flyway migration、改动数据库结构、升级框架版本。

## 验收标准

- [ ] JDK 21 环境执行 `gradle test --rerun-tasks`：56 tests 全绿（含 JWT/会话/报告/上传契约）
- [ ] JDK 21 环境执行 `gradle bootJar` 成功
- [ ] `eclipse-temurin:21-jre` 镜像可启动，`/actuator/health` 或等价健康检查通过（需 Docker 环境）
- [ ] Flyway V1–V8 顺序与 `ddl-auto: validate` 校验通过（由测试矩阵覆盖）
- [ ] Android 契约测试不受影响（无客户端变更）
- [ ] `git diff --check` PASS

## 非目标

- Spring Boot / Kotlin 大版本升级；Android 或 AI Service 运行时变更；自动证书签发；生产发布执行（需 Docker/CI 环境）。

## 执行状态（2026-08-14）

- ADR 状态：`accepted-by-architect`（构建验证通过；镜像启动验证依赖 Docker，延期）。
- 验证证据（本会话，工作区自带 Microsoft OpenJDK 21.0.12 LTS，`JAVA_HOME=.jdk21/jdk-21.0.12+8/Contents/Home`）：
  - 基线（Gradle 跑 JDK 21 + toolchain 17）：`gradle test --rerun-tasks` BUILD SUCCESSFUL
  - 升级后（toolchain 21）：`gradle clean test bootJar --rerun-tasks` BUILD SUCCESSFUL，**56 tests / 0 failures / 0 errors**
  - bootJar 产物字节码 major version = 65（Java 21）已实测
  - `git diff --check` PASS
- 已变更文件：`doctor-wang-mock-server/build.gradle.kts`（toolchain 21）、`doctor-wang-mock-server/Dockerfile`（temurin 21-jdk/21-jre）、`doctor-wang-mock-server/README.md`（Java 21 引用）
- 延期项：`eclipse-temurin:21-jre` 镜像启动与健康检查（需 Docker 环境）；Android 根构建不受影响（独立 Gradle build，保持 Java 17 编译目标）。
