#!/usr/bin/env bash
# Doctor-Wang 自动备份（数据库 + 报告文件）
# 安装：cron 每日 03:10（避开业务高峰）：
#   10 3 * * * /opt/doctor-wang/deploy/backup-db.sh >> /var/log/doctor-wang-backup.log 2>&1
# 恢复：gunzip -c backups/db-<ts>.sql.gz | docker exec -i doctor-wang-postgres psql -U doctor_wang doctor_wang
set -euo pipefail

BACKUP_DIR=/opt/doctor-wang/backups
mkdir -p "$BACKUP_DIR"
TS=$(date +%Y%m%d-%H%M%S)

echo "$(date '+%F %T') 开始备份 $TS"

# 1. PostgreSQL 数据库（含 pgvector 向量列）
docker exec doctor-wang-postgres pg_dump -U doctor_wang doctor_wang | gzip > "$BACKUP_DIR/db-$TS.sql.gz"
echo "  数据库: $(du -h "$BACKUP_DIR/db-$TS.sql.gz" | cut -f1)"

# 2. 报告原文件（从 backend 容器的挂载卷复制，无需额外镜像）
if docker cp doctor-wang-backend:/shared-report-files "$BACKUP_DIR/report-files-$TS" >/dev/null 2>&1; then
  tar czf "$BACKUP_DIR/report-files-$TS.tar.gz" -C "$BACKUP_DIR" "report-files-$TS"
  rm -rf "$BACKUP_DIR/report-files-$TS"
  echo "  报告文件: $(du -h "$BACKUP_DIR/report-files-$TS.tar.gz" | cut -f1)"
fi

# 3. 保留最近 7 天，清理更早的备份
find "$BACKUP_DIR" -name "db-*.sql.gz" -mtime +7 -delete
find "$BACKUP_DIR" -name "report-files-*.tar.gz" -mtime +7 -delete

echo "$(date '+%F %T') 备份完成"
