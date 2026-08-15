#!/usr/bin/env bash
# Doctor-Wang 服务器侧健康巡检（DEPLOY-001b 监控部分）
# 用途：cron 每 5 分钟检查本机 HTTPS 端点与容器状态；失败时写日志并可选推送飞书机器人告警。
# 安装（服务器）：
#   sudo cp health-monitor.sh /opt/doctor-wang/deploy/ && sudo chmod +x /opt/doctor-wang/deploy/health-monitor.sh
#   (sudo crontab -e) 添加：
#     */5 * * * * /opt/doctor-wang/deploy/health-monitor.sh
# 可选告警：在脚本顶部或环境设置 ALERT_WEBHOOK_URL=<飞书机器人 webhook>，失败即推送。
set -uo pipefail

LOG=/var/log/doctor-wang-health.log
BASE=https://localhost
ALERT_WEBHOOK_URL="${ALERT_WEBHOOK_URL:-}"

ok=1
curl -fsS -m 10 "$BASE/healthz" >/dev/null 2>&1 || ok=0
curl -fsS -m 10 "$BASE/actuator/health" >/dev/null 2>&1 || ok=0

if [ "$ok" = "1" ]; then
  echo "$(date '+%F %T') OK" >> "$LOG"
  # 只保留最近 2000 行，避免日志膨胀
  tail -n 2000 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"
  exit 0
fi

echo "$(date '+%F %T') DOWN (healthz/actuator 检查失败)" >> "$LOG"
# 附上容器状态，便于远程排查
docker compose --env-file /opt/doctor-wang/deploy/.env ps 2>/dev/null >> "$LOG" || true

if [ -n "$ALERT_WEBHOOK_URL" ]; then
  curl -fsS -m 10 -X POST "$ALERT_WEBHOOK_URL" -H 'Content-Type: application/json' \
    -d "{\"msg_type\":\"text\",\"content\":{\"text\":\"⚠️ Doctor-Wang 健康检查失败 $(date '+%F %T')，请检查 https://120.55.186.220\"}}" \
    >/dev/null 2>&1 || true
fi
exit 1
