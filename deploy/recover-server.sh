#!/usr/bin/env bash
# Doctor-Wang 服务器恢复脚本（在 ECS 上执行一次）
# 背景：2026-08-14 晚 1.6GB 无 swap 导致 OOM 卡死；本脚本=加固 + 内存限制部署 + 新版发布。
# 用法：在服务器 /opt/doctor-wang/deploy 下执行：
#   bash recover-server.sh <backend_jar_path> <apk_path>
set -euo pipefail

JAR="${1:-/opt/doctor-wang/backend-app.jar}"
APK="${2:-/opt/doctor-wang/doctor-wang-debug-v2.apk}"
cd /opt/doctor-wang/deploy

echo "==> 0. 停止旧栈"
docker compose --env-file .env down 2>/dev/null || true

echo "==> 1. 加固（swap + 内核参数 + Docker ulimit）"
bash setup-server.sh

echo "==> 2. 放置后端 jar（最小镜像方案，绕开 1.6GB 内存的 Gradle-in-Docker 构建）"
cp "$JAR" backend-app.jar

echo "==> 3. 构建镜像（ai-service 走阿里云 pip 镜像；后端用本地 jar）"
docker build -t deploy-backend:latest -f backend.Dockerfile . || echo "backend image 已存在则跳过"
docker compose --env-file .env build --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/ ai-service

echo "==> 4. 启动全栈"
docker compose --env-file .env up -d

echo "==> 5. 放置新版 APK 下载"
mkdir -p /srv/downloads
cp "$APK" /srv/downloads/doctor-wang-debug-v2.apk

echo "==> 6. 健康检查"
sleep 20
curl -sk https://localhost/healthz && echo
curl -sk https://localhost/actuator/health && echo
echo "==> 完成。外部访问: https://120.55.186.220（自签证书需浏览器放行）"
