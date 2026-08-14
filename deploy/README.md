# Doctor-Wang 单机部署说明

本目录提供 Doctor-Wang 第一版真实后端的单机部署资产，适用于一台 Linux 服务器通过 Docker Compose 启动：

- PostgreSQL
- Spring Boot backend
- Nginx TLS 终止与反向代理

这是一套部署基线，不是完整的生产发布方案。它不会自动签发或轮换证书，也不包含监控、告警、高可用或 AI Service 编排。

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
- `APP_AUTH_JWT_SECRET`（使用至少 32 个随机字符的独立 secret）
- `SERVER_PUBLIC_BASE_URL`
- `TLS_CERTS_DIR`

`TLS_CERTS_DIR` 必须是宿主机上的**绝对路径**，其中仅包含当前部署所需的：

- `fullchain.pem`：站点证书链
- `privkey.pem`：对应私钥

该目录不得提交到 Git、复制到镜像或置于项目工作树。部署账户应限制其权限；Compose 仅以只读方式把它挂载到 Nginx 容器的 `/etc/nginx/certs`。缺少这个目录或上述任一文件时，Nginx 配置检查/容器启动应失败，不能回退到 HTTP 服务。

## 4. 启动服务

```bash
cd deploy
docker compose --env-file .env up -d --build
```

启动完成后：

- Nginx 对外监听 `80` 和 `443`
- `80` 只返回相同 host、相同 path/query 的 HTTPS `308` 重定向；不会代理请求到 backend
- `443` 使用 `/etc/nginx/certs/fullchain.pem` 和 `/etc/nginx/certs/privkey.pem` 终止 TLS，并将请求代理到 compose 内部的 backend
- Backend 仅在 compose 内部暴露 `8080`
- PostgreSQL 数据保存在 Docker volume `postgres_data`

## 5. 健康检查

在证书 DNS 名称可解析且证书已经部署后检查：

```bash
curl -I http://your-domain.example/healthz
curl --fail --show-error https://your-domain.example/healthz
```

第一条命令应返回到同一 host 的 `https://` 重定向；第二条命令预期返回：

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

## 7. TLS 与证书运维

此配置由 Nginx 强制终止 TLS：端口 `80` 不会提供应用内容，只会重定向到 HTTPS；端口 `443` 使用只读证书挂载。不要在上游 LB/CDN 或防火墙中重新把外部 HTTP 流量转发给 backend，也不要直接发布 backend 的 `8080`。

证书的签发、续期和私钥保管由部署环境负责。每次替换证书后，先在受控环境运行静态检查，再滚动更新 Nginx：

```bash
cd deploy
docker compose --env-file .env config
docker compose --env-file .env run --rm nginx nginx -t
docker compose --env-file .env up -d --build
```

`nginx -t` 和 `docker compose config` 通过只表示静态配置有效；仍须在真实 TLS 端点上执行上一节的 HTTPS smoke test。不要把 `.env`、私钥、证书链、JWT secret、数据库密码或健康数据输出到 CI 日志。

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
- HTTPS 自动签发或证书轮换
- 生产级监控告警
- 高可用部署
