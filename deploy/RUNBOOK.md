# Doctor-Wang 阿里云 ECS 部署 Runbook

目标环境：阿里云 ECS · Ubuntu 24.04 · Docker Compose 单机部署（PostgreSQL + Spring Boot backend + AI Service + Nginx TLS）。
公网 IP：`120.55.186.220`（先 IP 后域名）。对外端口：80/443（TLS）。

> 配套文档：`deploy/README.md`（部署基线说明）、`docs/design/doctor-wang-target-architecture.md`（架构与 DB 基线）、`.ai/decisions/ADR-0009`（生产化加固方案）、`.ai/decisions/ADR-0008`（病毒扫描）。

## 0. 部署前必须确认的 3 件事

1. **数据库基线 = PostgreSQL**。仓库（Flyway V1–V9、Testcontainers 矩阵、pgvector 规划、compose）全部基于 PostgreSQL。私有笔记中的"MySQL/RDS 设想"**不采用**；如坚持 MySQL，须先出 ADR 并改造（成本高，不推荐）。
2. **HTTPS 需要域名**。ADR-0003 强制 TLS；Let's Encrypt 不能给纯 IP 签发受信任证书，Android release 默认拒绝自签证书。**建议：先给 `120.55.186.220` 绑定一个域名（DNS A 记录），再部署**。
3. **病毒扫描开关**。`VIRUS_SCAN_ENABLED=true` 需 ClamAV 端点（compose 已提供可选 `scan` profile，见 §3）；首次部署可先 `false`（记录为已知缺口），随 DEPLOY-001b 启用。

## 1. 服务器准备（一次性）

```bash
# 1.1 安装 Docker + compose plugin（Ubuntu 24.04）
curl -fsSL https://get.docker.com | sh
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"   # 重新登录后生效
docker compose version            # 应显示 v2

# 1.2 阿里云控制台安全组（必须）
#   放行：80 (HTTP→308), 443 (HTTPS)
#   禁止对外：8080（backend 仅容器内网）

# 1.3 TLS 证书目录（宿主机，chmod 700，仅 root，绝不入 Git/镜像）
sudo mkdir -p /etc/doctor-wang/tls
sudo chmod 700 /etc/doctor-wang/tls
```

## 2. 证书（域名方式）

```bash
# 2.1 DNS：A 记录 your-domain → 120.55.186.220（TTL 300，等待生效）
# 2.2 certbot 签发（Nginx 尚在容器内，用 webroot 或 standalone 均需先停 80 占用）：
sudo apt-get install -y certbot
sudo certbot certonly --standalone -d your-domain.example \
  --email you@example.com --agree-tos --no-eff-email
# 2.3 软链到部署目录：
sudo ln -s /etc/letsencrypt/live/your-domain.example/fullchain.pem /etc/doctor-wang/tls/fullchain.pem
sudo ln -s /etc/letsencrypt/live/your-domain.example/privkey.pem  /etc/doctor-wang/tls/privkey.pem
# 2.4 自动续期（crontab）：
echo "15 3 * * * root certbot renew --quiet --deploy-hook 'systemctl reload nginx 2>/dev/null || true'" | sudo tee /etc/cron.d/certbot-renew
```

> 纯 IP 直连（无域名）：只能自签证书，Android release 默认拒绝 → 仅限调试，不满足生产要求。

## 3. 环境变量（deploy/.env）

```bash
cd deploy && cp .env.example .env && chmod 600 .env && vi .env
```

必填项：
- `POSTGRES_PASSWORD`：独立强密码
- `APP_AUTH_JWT_SECRET`：≥32 随机字符（`openssl rand -base64 48`）
- `AI_SERVICE_INTERNAL_TOKEN`：长随机串
- `TLS_CERTS_DIR=/etc/doctor-wang/tls`
- `AI_SERVICE_ENABLED=true`
- `DW_AI_LLM_ENDPOINT` / `DW_AI_LLM_API_KEY` / `DW_AI_LLM_MODEL`：DeepSeek（真实生成，已验证）
- `DW_AI_EMBEDDING_ENDPOINT` / `DW_AI_EMBEDDING_API_KEY` / `DW_AI_EMBEDDING_MODEL=embedding-3` / `DW_AI_EMBEDDING_DIMENSIONS=2048`：智谱（真实检索，已验证）
- `DW_AI_OCR_ENDPOINT` / `DW_AI_OCR_API_KEY` / `DW_AI_OCR_MODEL=qwen-vl-ocr`：通义（真实 OCR，已验证）

> 密钥只写进 `.env`（600 权限），不进 Git、不进镜像、不进 CI 日志。生产建议后续用密钥管理（KMS/Secrets）替代 .env。

可选（ClamAV 病毒扫描，SEC-001/ADR-0008）：
```bash
VIRUS_SCAN_ENABLED=true
VIRUS_SCAN_HOST=clamav
VIRUS_SCAN_PORT=3310
# 以 scan profile 启动 ClamAV 容器：docker compose --profile scan --env-file .env up -d --build
```

## 4. 获取代码并启动

```bash
git clone git@github.com:iPuuuuu/doctor-wang.git  # 或 scp 已有目录
cd doctor-wang/deploy
docker compose --env-file .env config          # 静态校验
docker compose --env-file .env up -d --build   # 首次构建较久
docker compose --env-file .env ps              # 全部 healthy
```

## 5. 验证清单（逐项）

```bash
# 5.1 TLS：HTTP 只 308，HTTPS 正常
curl -I http://your-domain.example/healthz            # → 308 Location: https://...
curl --fail https://your-domain.example/healthz        # → {"status":"ok"}
curl --fail https://your-domain.example/actuator/health # → {"status":"UP"}

# 5.2 注册/登录（JWT）
curl -s -X POST https://your-domain.example/api/register -H 'Content-Type: application/json' \
  -d '{"account":"demo@test.local","password":"secret123","name":"Demo","age":30,"height":170,"weight":65}'

# 5.3 上传 + 处理状态（multipart，带 Authorization Bearer <token>）
curl -s -X POST https://your-domain.example/api/v1/reports -H "Authorization: Bearer $TOKEN" \
  -F "file=@/path/to/de-identified-report.pdf"

# 5.4 安心话聊（疑病症焦虑缓解）
curl -s -X POST https://your-domain.example/api/v1/chat/anxiety \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"我总担心自己得了重病"}'
# 应返回循证安抚 + warnings（医疗边界）+ citations（如有报告）

# 5.5 Android release 对接
./gradlew assembleRelease -PdoctorWangReleaseBaseUrl=https://your-domain.example/
# 安装后验证：登录 → 上传 → AI 问诊（真实 OCR/检索/生成链路）

# 5.5 病毒扫描（如启用 scan profile）：上传 EICAR 测试文件应被拒（MALWARE_DETECTED）
```

## 6. 运维

- **更新**：`docker compose --env-file .env up -d --build`
- **备份**：`docker compose exec postgres pg_dump -U doctor_wang doctor_wang > backup.sql`；`report_files`/`postgres_data` 卷定期快照
- **日志**：`docker compose logs -f backend ai-service nginx`
- **回滚**：保留上一版镜像 tag，`docker compose up -d --build` 前先 `docker tag` 旧镜像

## 6.1 故障恢复（OOM 卡死，2026-08-14 事故）

1.6GB 无 swap 的小内存 VPS 上，PostgreSQL + JVM + AI 服务并发时可能 OOM 整机挂起（症状：SSH `banner exchange` 超时、TCP 通但应用层无响应）。恢复步骤：

1. 阿里云控制台 **强制重启** 实例（VNC 若也卡死则先"停止"再"启动"）。
2. SSH 恢复后，把新版后端 jar 与 APK scp 到服务器（若本地已构建）：
   ```bash
   scp doctor-wang-mock-server/build/libs/*.jar root@120.55.186.220:/opt/doctor-wang/backend-app.jar
   scp app/build/outputs/apk/debug/app-debug.apk root@120.55.186.220:/opt/doctor-wang/doctor-wang-debug-v2.apk
   ```
3. 在服务器执行一键恢复脚本（加固 swap/内核参数 → 最小镜像后端 → compose 启动 → 端到端冒烟）：
   ```bash
   cd /opt/doctor-wang/deploy && bash recover-server.sh
   ```
4. 验证 `docker compose --env-file .env ps` 全部 healthy；脚本已自动做登录 + 安心话聊冒烟。

> 加固内容（`setup-server.sh`）：2G swap、`tcp_max_syn_backlog`/`somaxconn` 缓解半连接堆积、Docker ulimit、容器内存限制（JVM `-Xmx384m`、PG `shared_buffers=64MB`）。

## 7. 部署后待办（DEPLOY-001b，见 ADR-0009）

- 证书自动签发/轮换接入（当前 cron 手动续期）
- 监控/告警（actuator metrics + 平台探针/告警通道）
- 高可用（backend 多副本、托管 PostgreSQL、对象存储化）
- CI 流水线已就绪（`.github/workflows/ci.yml`），部署服务器接 webhook 可自动构建
