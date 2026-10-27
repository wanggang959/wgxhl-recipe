#!/usr/bin/env bash
# 每日 MySQL 备份：本地保留 7 天，并上传到腾讯云 COS 的 backups/mysql/ 前缀。
# 由 root crontab 调用。数据库与 COS 凭据取自运行中的后端容器（.env 里的 JDBC URL 含未转义 &，
# 不能用 source 解析），容器不可用时回退到按行读取 .env。
set -euo pipefail
umask 077

ENV_FILE="${ENV_FILE:-/opt/1panel/docker/compose/wgxhl-recipe-backend/.env}"
BACKUP_DIR="${BACKUP_DIR:-/opt/backups/mysql}"
KEEP_DAYS="${KEEP_DAYS:-7}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-1Panel-mysql-wx8w}"
APP_CONTAINER="${APP_CONTAINER:-wgxhl-recipe-backend}"
COS_PREFIX="${COS_PREFIX:-backups/mysql}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

env_value() {
  local key="$1" value=""
  if docker inspect "$APP_CONTAINER" >/dev/null 2>&1; then
    value=$(docker exec "$APP_CONTAINER" printenv "$key" 2>/dev/null || true)
  fi
  if [ -z "$value" ] && [ -f "$ENV_FILE" ]; then
    value=$(awk -v key="$key" 'index($0, key "=") == 1 { sub("^" key "=", ""); print; exit }' "$ENV_FILE")
  fi
  printf '%s' "$value"
}

DB_URL="$(env_value SPRING_DATASOURCE_URL)"
DB_USER="$(env_value SPRING_DATASOURCE_USERNAME)"
DB_PASS="$(env_value SPRING_DATASOURCE_PASSWORD)"
DB_NAME=$(printf '%s' "$DB_URL" | sed -E 's#^jdbc:mysql://[^/]+/([^?]+).*$#\1#')

if [ -z "$DB_NAME" ] || [ "$DB_NAME" = "$DB_URL" ] || [ -z "$DB_USER" ]; then
  echo "$(date '+%F %T') 无法解析数据库连接信息（URL/用户名）" >&2
  exit 1
fi

mkdir -p "$BACKUP_DIR"
STAMP=$(date +%Y%m%d-%H%M%S)
FILE="$BACKUP_DIR/${DB_NAME}-${STAMP}.sql.gz"

docker exec -e MYSQL_PWD="$DB_PASS" "$MYSQL_CONTAINER" \
  mysqldump -u"$DB_USER" \
  --single-transaction --quick --no-tablespaces --routines --triggers --set-gtid-purged=OFF \
  "$DB_NAME" 2>/dev/null | gzip -9 > "$FILE"

if [ ! -s "$FILE" ] || ! gzip -t "$FILE" 2>/dev/null; then
  echo "$(date '+%F %T') 备份文件校验失败: $FILE" >&2
  rm -f "$FILE"
  exit 1
fi

echo "$(date '+%F %T') 本地备份完成: $FILE ($(du -h "$FILE" | cut -f1))"

find "$BACKUP_DIR" -name "${DB_NAME}-*.sql.gz" -mtime +"$KEEP_DAYS" -delete

COS_SECRET_ID="$(env_value COS_SECRET_ID)" \
COS_SECRET_KEY="$(env_value COS_SECRET_KEY)" \
COS_REGION="$(env_value COS_REGION)" \
COS_BUCKET="$(env_value COS_BUCKET)" \
python3 "$SCRIPT_DIR/cos-upload.py" "$FILE" "${COS_PREFIX}/$(basename "$FILE")"

echo "$(date '+%F %T') 备份任务完成"
