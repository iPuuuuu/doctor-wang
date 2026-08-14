---
id: ADR-0008
title: Production virus scanning for report uploads with fail-closed semantics
status: accepted-by-architect
date: 2026-08-14
owners: [architect-agent]
---

# ADR-0008：报告上传生产态病毒扫描（fail-closed）

## 背景

- AGENTS.md 强制：文件上传必须校验 MIME、magic number、大小、hash、用户归属、**病毒扫描**和对象生命周期；AI Service 不得读取未授权对象。
- 现状（SEC-001 前）：`VirusScanner` 接口 + `NoopVirusScanner` 开发占位（注释明确"Production must replace this"），上传无条件放行；MIME 白名单、magic number、SHA-256、幂等、签名 URL 已有。
- 用户决策（2026-08-14）：开工 SEC-001，接受上传语义变化——**扫描器不可用时 fail-closed 拒绝，绝不默认放行**。

## 决策

1. **新增 `ClamAvVirusScanner`**（clamd `zINSTREAM` 协议客户端）：按配置 `app.virus-scan.{enabled,host,port,connect-timeout-ms,read-timeout-ms}` 启用。
2. **fail-closed 语义（硬性）**：连接失败、超时、IO 错误、畸形响应 → `ScanResult(clean=false, reason=SCANNER_UNAVAILABLE)` → 上传返回 `SCAN_UNAVAILABLE` 业务错误（HTTP 处理沿用 GlobalExceptionHandler），绝不静默放行。
3. **检出恶意文件** → `MALWARE_DETECTED`（与扫描服务不可用区分开，便于运维区分故障与威胁）。
4. **条件装配**：`app.virus-scan.enabled=false`（默认，开发态）→ `NoopVirusScanner`（保留 dev 体验，注释明示生产缺口）；`=true` → `ClamAvVirusScanner`。二者互斥，`VirusScanConfigTest` 覆盖。
5. **对象生命周期**（文件过期清理）不在本 ADR 范围，记为后续切片（见 backlog 备注）。

## 备选方案与取舍

- 托管扫描 API（如 VirusTotal）：需外发健康数据，隐私与合规风险 — 不选择。
- 仅信任客户端 MIME：已被 magic number 校验覆盖 — 不选择。
- 扫描失败时降级放行（fail-open）：用户确认拒绝 — 不选择。
- ClamAV 容器内联（docker-compose 增加 clamav 服务）：已规划，随 DEPLOY-001/Docker 环境验证落地；代码层面通过 host/port 配置解耦。

## 迁移与回滚

- 启用：设置 `VIRUS_SCAN_ENABLED=true` + 可达的 ClamAV 端点（生产编排新增 clamav 服务）。
- 回滚：`VIRUS_SCAN_ENABLED=false` 回到 Noop（仅 dev）；生产回滚须先确保扫描可用，否则上传被拒（fail-closed 是特性而非故障）。
- 不修改：既有上传校验链（MIME/magic/大小/幂等）、migration、存储结构。

## 验收标准

- [x] `ClamAvVirusScannerTest`：干净放行、检出 malware、连接拒绝、无响应、畸形响应、超时 → 6 用例全绿（fail-closed 路径全覆盖）
- [x] `VirusScanConfigTest`：enabled=false/默认 → Noop；enabled=true → ClamAv（3 用例）
- [x] `ReportUploadScanBehaviorTest`：MALWARE_DETECTED / SCAN_UNAVAILABLE / 干净放行到持久化 / 失败不触碰持久层（4 用例）
- [x] 全量后端测试 69 tests / 0 failures（JDK 21）
- [x] `bootJar` 构建成功、`git diff --check` PASS
- [ ] 真实 ClamAV 集成冒烟（clamd 容器）— 需 Docker 环境，延期

## 非目标

- 对象生命周期/过期清理（后续切片）；生产证书；AI Service 侧文件读取授权（随 REPORT-AI-001 生产化）。
