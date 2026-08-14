## Agent Receipt

Task ID: SEC-001
Agent: architect
Role: Architect（生产化阶段，用户授权后实施）
Date: 2026-08-14

### Status
ACCEPT（本地全量验证通过；真实 ClamAV 集成冒烟延 Docker）

### Summary
报告上传病毒扫描生产化（ADR-0008，fail-closed）：
1. 新增 `ClamAvVirusScanner`（clamd zINSTREAM 协议客户端，连接/读超时可配）
2. **fail-closed**：扫描器不可达/超时/畸形响应 → `SCAN_UNAVAILABLE` 拒绝上传，绝不默认放行；检出恶意文件 → `MALWARE_DETECTED`
3. 条件装配：`app.virus-scan.enabled` 默认 false → `NoopVirusScanner`（开发态，注释明示生产缺口）；true → `ClamAvVirusScanner`
4. `VirusScanProperties`（@ConfigurationProperties + 校验）+ `ReportProcessingService` 扫描原因映射
5. 13 个新测试（扫描器 6 + 装配 3 + 上传决策 4）

### Changed Files
- `service/ClamAvVirusScanner.kt` — 新增（生产扫描器，fail-closed）
- `service/VirusScanner.kt` — Noop 条件化 + ScanReasons 常量
- `config/VirusScanProperties.kt` — 新增（配置 + 校验）
- `service/ReportProcessingService.kt` — 扫描失败原因映射（MALWARE / SCANNER_UNAVAILABLE）
- `DoctorWangMockServerApplication.kt` — 注册 VirusScanProperties
- `src/main/resources/application.yml` — app.virus-scan 配置节（VIRUS_SCAN_* 环境变量）
- 测试：`ClamAvVirusScannerTest.kt`（6）、`VirusScanConfigTest.kt`（3）、`ReportUploadScanBehaviorTest.kt`（4）— 新增
- `.ai/decisions/ADR-0008-upload-virus-scanning.md` — 新增 ADR

### Tests Run
```
JAVA_HOME=<ws>/.jdk21/... GRADLE_USER_HOME=<ws>/.gradle-home \
<cached>/gradle-8.10.2/bin/gradle test --rerun-tasks bootJar --no-daemon \
-Dkotlin.compiler.execution.strategy=in-process
```

### Test Result
PASS — BUILD SUCCESSFUL；后端 **69 tests / 0 failures / 0 errors**（14 套件，+13 SEC-001 用例）；`bootJar` SUCCESSFUL；`git diff --check` PASS。全项目累计 Android 15 + backend 69 + AI 86 = **170 tests PASS**。

### Evidence
- 扫描器 6 用例覆盖 fail-closed 全路径（干净/检出/连接拒绝/无响应/畸形/超时）
- 装配 3 用例验证 enabled 开关互斥
- 上传决策 4 用例验证 MALWARE_DETECTED / SCAN_UNAVAILABLE / 干净持久化 / 失败不触持久层
- ADR-0008：`.ai/decisions/ADR-0008-upload-virus-scanning.md`

### Known Risks
- 真实 ClamAV 集成（clamd 容器）冒烟未执行 — P1，需 Docker 环境
- 开发默认 Noop 仍是不扫描的占位（production gap），已通过 enabled 开关与注释明示 — P2
- 对象生命周期（文件过期清理）未实现 — P2，记录为后续切片

### Blockers
- 无（本地验证全部通过；ClamAV 集成冒烟为环境延期项）

### Unfinished Items
- ClamAV 容器 + 真实冒烟（docker-compose 增加 clamav 服务随 DEPLOY-001）— 需 Docker
- 对象生命周期/过期清理 — 后续切片

### Recommended Next Action
DEPLOY-001（部署加固：证书自动化、监控告警、clamav 服务编排、HA）方案与实施；或提供 Docker 环境补跑 ClamAV/PostgreSQL/TLS 冒烟。
