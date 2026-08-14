## Agent Receipt

Task ID: DEPLOY-001c (ECS production deployment)
Agent: architect
Role: Architect（用户要求执行部署）
Date: 2026-08-14

### Status
ACCEPT（生产栈在阿里云 ECS 部署成功并全链路验证；自签证书为已知限制）

### Summary
在阿里云 ECS（120.55.186.220, Ubuntu 24.04, 1.6GB RAM）完成 Doctor-Wang 生产部署：
1. 代码传输：本地打包（3.1MB，排除大目录）scp 到 /opt/doctor-wang
2. 配置：生成强随机 POSTGRES_PASSWORD/JWT_SECRET/内部 token；写入 .env（含三把 AI key，chmod 600，不入库）；自签 TLS 证书（无域名，明示限制）
3. 镜像：ai-service（阿里云 pip 镜像加速）构建成功；后端因 1.6GB 内存 Gradle 构建卡死，改由**本地已构建 jar 打包最小镜像**（deploy-backend，temurin:21-jre）
4. 部署中修复的部署级 bug：
   - compose postgres:16 无 pgvector → 改 pgvector/pgvector:pg16（V6/V9 依赖）
   - compose ai-service 未透传 DW_AI_* provider env → 补全（含 DW_AI_DATABASE_URL 指向共享 PostgreSQL）
5. 验证：4 容器 healthy；Flyway V1-V10 应用；HTTPS /healthz+actuator UP；E2E（注册→上传→COMPLETED→聊天 citation 1 + DeepSeek 真实回答）；pgvector 落库 2048 维；**外部 https://120.55.186.220 200 OK**

### Changed Files
- `deploy/docker-compose.yml` — postgres 镜像 pgvector、ai-service provider env 透传、backend JVM 内存限制
- `doctor-wang-mock-server/Dockerfile` — 构建期 Gradle/JVM 内存限制
- `deploy/backend.Dockerfile` — 新增（服务器本地 jar 最小镜像，未入库，仅服务器）

### Tests Run
```
服务器：docker compose up -d（镜像构建/拉取）
验证：curl -sk https://localhost/{healthz,actuator/health}；python E2E（https://localhost）
外部：curl -sk https://120.55.186.220/healthz → 200
```

### Test Result
PASS — 全链路验证通过；CI 也全绿（含 Testcontainers 集成测试，本轮修复 3 个测试/部署 bug）。

### Known Risks
- 自签证书：浏览器/Android 会有安全警告；生产用户使用前需绑定域名 + 受信任证书（RUNBOOK §2）
- 1.6GB 内存小 VPS：后端 JVM 限 -Xmx384m；并发用户多时可能紧张（swap=0）
- ClamAV 病毒扫描未启用（VIRUS_SCAN_ENABLED=false，需 scan profile + 内存考量）
- 80 端口外部不可达（安全组可能未放行 80；HTTPS 443 正常）

### Blockers
- 无（部署完成；域名/证书/监控为后续生产化项）

### Unfinished Items
- 绑定域名 + Let's Encrypt 受信任证书
- 监控/告警（DEPLOY-001b）
- 内存扩容或 swap（生产负载增长时）

### Recommended Next Action
告知用户部署完成 + 安全组/证书注意事项；后续按需启用 ClamAV/监控/域名。
