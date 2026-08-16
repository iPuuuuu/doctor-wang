#!/usr/bin/env bash
# Doctor-Wang 服务器侧健康巡检（DEPLOY-001b 监控部分）
# 用途：cron 每 5 分钟检查 HTTPS 端点、容器状态与资源水位（内存/磁盘/swap）。
# 失败或资源告警时写日志并可选推送飞书机器人告警。
# 安装（服务器）：
#   sudo cp health-monitor.sh /opt/doctor-wang/deploy/ && sudo chmod +x /opt/doctor-wang/deploy/health-monitor.sh
#   (sudo crontab -e) 添加：
#     */5 * * * * /opt/doctor-wang/deploy/health-monitor.sh
# 可选告警：ALERT_WEBHOOK_URL=<飞书机器人 webhook>
set -uo pipefail

LOG=/var/log/doctor-wang-health.log
# 用 IPv4 显式地址：localhost 可能解析到 ::1，而 nginx 只监听 IPv4，会导致误报 DOWN
BASE=https://127.0.0.1
ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL:-}"
# 资源告警阈值：可用内存 < 120MB（1.6GB 机器 OOM 前兆）、磁盘使用 > 85%、swap 使用 > 90%
MEM_ALERT_MB=120
DISK_ALERT_PCT=85
SWAP_ALERT_PCT=90

ok=1
# -k：自签证书（域名+正式证书前）不校验；-4：nginx 仅监听 IPv4
curl -4 -kfsS -m 10 "$BASE/healthz" >/dev/null 2>&1 || ok=0
curl -4 -kfsS -m 10 "$BASE/actuator/health" >/dev/null 2>&1 || ok=0

# 资源水位（非致命告警，单独记录）
WARN=""
AVAIL=$(free -m | awk '/Mem/{print $7}')
SWAP_TOTAL=$(free -m | awk '/Swap/{print $2}')
SWAP_USED=$(free -m | awk '/Swap/{print $3}')
DISK_PCT=$(df / | awk 'NR==2 {gsub("%","",$5); print $5}')
if [ -n "$AVAIL" ] && [ "$AVAIL" -lt "$MEM_ALERT_MB" ]; then WARN="$WARN 内存紧张(${AVAIL}MB)"; fi
if [ -n "$DISK_PCT" ] && [ "$DISK_PCT" -gt "$DISK_ALERT_PCT" ]; then WARN="$WARN 磁盘(${DISK_PCT}%)"; fi
if [ -n "$SWAP_TOTAL" ] && [ "$SWAP_TOTAL" -gt 0 ] && [ -n "$SWAP_USED" ] && [ $((SWAP_USED * 100 / SWAP_TOTAL)) -gt "$SWAP_ALERT_PCT" ]; then WARN="$WARN swap(${SWAP_USED}/${SWAP_TOTAL}MB)"; fi

if [ "$ok" = "1" ] && [ -z "$WARN" ]; then
  echo "$(date '+%F %T') OK" >> "$LOG"
else
  STATUS="DOWN"
  [ "$ok" = "1" ] && STATUS="WARN"
  echo "$(date '+%F %T') $STATUS (healthz/actuator:$ok${WARN})" >> "$LOG"
  if [ "$ok" = "0" ]; then
    docker compose --env-file /opt/doctor-wang/deploy/.env ps 2>/dev/null >> "$LOG" || true
  fi
  if [ -n "$ALERT_WEBHOOK_URL" ]; then
    curl -fsS -m 10 -X POST "$ALERT_WEBHOOK_URL" -H 'Content-Type: application/json' \
      -d "{\"msg_type\":\"text\",\"content\":{\"text\":\"⚠️ Doctor-Wang 巡检: $STATUS $(date '+%F %T')${WARN}，请检查 https://120.55.186.220\"}}" \
      >/dev/null 2>&1 || true
  fi
fi

# 只保留最近 2000 行，避免日志膨胀
tail -n 2000 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
[ "$ok" = "1" ] && [ -z "$WARN" ]
