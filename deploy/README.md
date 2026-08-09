# Doctor-Wang 单机部署说明

本目录提供 Doctor-Wang 第一版真实后端的单机部署资产，适用于一台 Linux 服务器通过 Docker Compose 启动：

- PostgreSQL
- Spring Boot backend
- Nginx 反向代理

## 1. 前置要求

服务器需已安装：

- Docker
- Docker Compose Plugin

推荐系统：

- Ubuntu 22.04+
- Debian 12+

## 2. 目录说明

- `docker-compose.yml`：启动 postgres、backend、nginx
- `nginx/default.conf`：Nginx 反向代理配置
- `.env.example`：部署环境变量模板

## 3. 首次部署

在服务器上进入项目目录后：

```bash
cd deploy
cp .env.example .env
```

然后修改 `.env`，至少替换：

- `POSTGRES_PASSWORD`
- `APP_AUTH_TOKEN_PREFIX`
- `SERVER_PUBLIC_BASE_URL`

## 4. 启动服务

```bash
cd deploy
docker compose --env-file .env up -d --build
```

启动完成后：

- Nginx 对外监听 `80`
- Backend 仅在 compose 内部暴露 `8080`
- PostgreSQL 数据保存在 Docker volume `postgres_data`

## 5. 健康检查

本机检查：

```bash
curl http://127.0.0.1/healthz
```

预期返回：

```json
{"status":"ok"}
```

## 6. Android 对接

Android release 构建时，将后端 HTTPS 地址注入：

```bash
./gradlew assembleRelease -PdoctorWangReleaseBaseUrl=https://your-domain.example/
```

如果用环境变量：

```bash
export DOCTOR_WANG_RELEASE_BASE_URL=https://your-domain.example/
./gradlew assembleRelease
```

## 7. HTTPS 接入建议

当前仓库内的 Nginx 配置仅处理 HTTP 反向代理。

生产环境建议使用以下任一方式补上 HTTPS：

1. 云厂商 LB / CDN 终止 TLS，再转发到本机 80
2. 将 Nginx 扩展为 443 + 证书挂载
3. 改用 Caddy 自动申请 Let's Encrypt 证书

在 HTTPS 没接好之前，不建议给真实用户使用，因为登录 token 与健康数据不应通过明文 HTTP 传输。

## 8. 数据持久化

PostgreSQL 使用命名卷：

- `postgres_data`

因此在容器重启后，聊天记录、健康档案、报告记录、session 数据会继续保留。

## 9. 更新部署

代码更新后执行：

```bash
cd deploy
docker compose --env-file .env up -d --build
```

## 10. 当前限制

当前部署资产已覆盖：

- 登录
- 档案读取
- 聊天消息持久化
- 报告记录持久化
- token 鉴权

但暂未覆盖：

- 真正的 multipart 文件上传
- OCR / 结构化解析
- HTTPS 自动签发
- 生产级监控告警
- 高可用部署
