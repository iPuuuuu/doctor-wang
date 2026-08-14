---
id: ADR-0009
title: Deployment production hardening — health/monitoring, TLS provisioning, HA, ClamAV orchestration
status: partially-accepted (DEPLOY-001a accepted-by-architect; DEPLOY-001b proposed)
date: 2026-08-14
owners: [architect-agent]
---

# ADR-0009：部署生产化加固方案

## 背景

- `deploy/` 现状：docker-compose（postgres/backend/ai-service，含部分 healthcheck）+ Nginx TLS 终止（证书 volume 手工供给，`/etc/nginx/certs`）+ HTTP→HTTPS 重定向。
- 缺口（生产化审计，2026-08-14）：
  1. **后端无健康检查端点**：compose 中 backend 无 healthcheck，监控探针无处可挂。
  2. **证书供给无自动化**：全链路/私钥手工 volume，无 Let's Encrypt/托管证书流程；证书/私钥不得入仓（ADR-0003）。
  3. **无监控/告警**：无 metrics 暴露、无日志聚合、无告警通道。
  4. **无高可用**：单 backend 副本、无 DB HA；报告文件 volume 单点。
  5. **无 ClamAV 编排**：SEC-001 的 ClamAvVirusScanner 需部署侧提供 clamd 端点（`VIRUS_SCAN_HOST/PORT`）。
  6. **无 CI**：构建/测试/安全扫描无流水线。

## 决策（分两个可独立验收的切片）

### DEPLOY-001a：后端健康检查端点（本地可验证）
- 引入 `spring-boot-starter-actuator`，暴露 `/actuator/health`（含 readiness 语义），配置仅 `health` 端点对外（不暴露 env/beans 等敏感端点）。
- compose backend 增加 healthcheck（依赖 `/actuator/health`），`depends_on` 语义更准确。
- 测试：`/actuator/health` 返回 200 UP（无 DB 依赖的 liveness 语义；readiness 依赖 DB 由部署层探针处理）。

### DEPLOY-001b：生产环境编排（需 Docker/生产环境验证）
- **TLS 供给**：优先托管平台 TLS（Render/Railway/Fly），自管服务器走 Let's Encrypt webroot/dns-01 + 自动续期 runbook；证书/私钥永不入仓。
- **监控/告警**：actuator metrics 暴露 + 平台探针；日志结构化；告警通道（平台内置或自建）。
- **HA**：backend 多副本（无状态侧）+ PostgreSQL 托管/HA 方案 + 报告文件对象存储化（本地 volume 单点为已知限制）。
- **ClamAV 编排**：compose 增加 `clamav/clamav` 服务，backend 以 `VIRUS_SCAN_HOST=clamav:3310` + `VIRUS_SCAN_ENABLED=true` 接入。
- **CI**：GitHub Actions 或等价流水线：Android 单测+构建、后端 test（JDK 21）、AI pytest、`git diff --check`。

## 备选方案与取舍

- 云 LB 终止 TLS：部署资产无法审计真实终止策略，ADR-0003 已排除作为基线；托管平台 TLS 属同类，作为上线入口选项而非仓库强制。
- 自建 Prometheus/Grafana：超出当前规模，优先平台内置监控；自建栈留待规模增长。
- actuator 全端点暴露：泄漏 env/beans 等敏感信息 — 仅白名单 health 端点。

## 迁移与回滚

- DEPLOY-001a：加依赖 + 配置 + 测试；回滚 = 移除依赖与配置（无 schema/数据影响）。
- DEPLOY-001b：逐项在 Docker/生产环境验证；回滚按各组件 runbook（证书回退 volume、关闭 ClamAV 接入等）。

## 验收标准

### DEPLOY-001a（2026-08-14 已验收）
- [x] `GET /actuator/health` → `{"status":"UP"}`（ActuatorHealthTest，最小 actuator 上下文，MockMvc 实测）
- [x] compose backend healthcheck 声明（curl /actuator/health；镜像内置 curl）
- [x] 后端全量测试 70/70 PASS（JDK 21）+ bootJar + `git diff --check`
- [ ] compose healthcheck 真实容器验证 — 需 Docker，延期

### DEPLOY-001b（需 Docker/生产环境）
- [ ] 托管/LE 证书自动供给且私钥不入仓；`nginx -t` 通过
- [ ] 监控探针与告警通道就绪
- [ ] backend 多副本 + DB/对象存储 HA 拓扑
- [ ] ClamAV 服务可达，`VIRUS_SCAN_ENABLED=true` 冒烟（上传干净/检出文件路径）
- [ ] CI 流水线全绿

## 非目标

- 后端框架升级、数据库分片、多租户、SOC2 级合规文档；上述为后续阶段。
