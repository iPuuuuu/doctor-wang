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
# VIRUS_SCAN_ENABLED=true 时（fail-closed）必须带上 ClamAV 容器，否则所有上传会被扫描器拒绝。
# 首次启动 ClamAV 会下载签名库（数分钟），期间上传按 ADR-0008 fail-closed 拒绝，属预期。
VIRUS_SCAN=$(grep -E '^VIRUS_SCAN_ENABLED=' .env 2>/dev/null | tail -1 | cut -d= -f2 | tr -d '[:space:]' || true)
if [ "${VIRUS_SCAN:-true}" = "true" ]; then
  echo "    VIRUS_SCAN_ENABLED=true → 以 scan profile 启动（含 ClamAV，内存受限见 RUNBOOK §6.1）"
  docker compose --env-file .env --profile scan up -d
else
  docker compose --env-file .env up -d
fi

echo "==> 5. 放置新版 APK 下载"
mkdir -p /srv/downloads
cp "$APK" /srv/downloads/doctor-wang-debug-v2.apk

echo "==> 6. 健康检查"
sleep 20
curl -sk https://localhost/healthz && echo
curl -sk https://localhost/actuator/health && echo

echo "==> 7. 端到端冒烟（demo 账号登录 + 安心话聊全链路）"
BASE=https://localhost
for i in $(seq 1 10); do
  LOGIN=$(curl -sk -X POST "$BASE/api/login" -H 'Content-Type: application/json' \
    -d '{"account":"demo@doctorwang.app","password":"123456"}' 2>/dev/null) || LOGIN=""
  TOKEN=$(printf '%s' "$LOGIN" | python3 -c 'import sys,json; print(json.load(sys.stdin).get("token",""))' 2>/dev/null || echo "")
  [ -n "$TOKEN" ] && break
  echo "    等待后端就绪 ($i/10)..."; sleep 10
done
if [ -z "$TOKEN" ]; then
  echo "    ❌ 登录失败（后端未就绪或 demo 账号不可用）: $LOGIN"; exit 1
fi
echo "    ✅ 登录成功"
CHAT=$(curl -sk -X POST "$BASE/api/v1/chat/anxiety" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"我最近总担心自己得了大病，有点焦虑，能帮我聊聊吗"}' 2>/dev/null)
printf '%s' "$CHAT" | python3 -c '
import sys, json
raw = sys.stdin.read()
try:
    d = json.loads(raw)
    msg = d.get("message", "")
    print("    ✅ 安心话聊回复(" + str(len(msg)) + " 字): " + msg[:60] + ("…" if len(msg) > 60 else ""))
except Exception:
    print("    ❌ 回复解析失败: " + raw[:200]); sys.exit(1)
'
echo "==> 完成。外部访问: https://120.55.186.220（自签证书需浏览器放行）"
