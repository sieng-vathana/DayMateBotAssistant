#!/usr/bin/env bash
set -euo pipefail

APP_DIR=/opt/daymate
DATA_DIR=/var/lib/daymate/data
LOG_DIR=/var/log/daymate
ENV_FILE=/etc/daymate/daymate.env

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash $0"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y --no-install-recommends openjdk-17-jre-headless curl ca-certificates

id -u daymate >/dev/null 2>&1 || useradd --system --home "$APP_DIR" --shell /usr/sbin/nologin daymate

mkdir -p "$APP_DIR" "$DATA_DIR" "$LOG_DIR" /etc/daymate
chown -R daymate:daymate "$APP_DIR" /var/lib/daymate "$LOG_DIR"

if [[ ! -f "$ENV_FILE" ]]; then
  cat > "$ENV_FILE" <<'EOF'
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8000
SERVER_ADDRESS=0.0.0.0
TELEGRAM_BOT_TOKEN=
TELEGRAM_ALLOWED_USER_IDS=
TELEGRAM_BOT_TIMEZONE=Asia/Bangkok
TELEGRAM_DIGEST_ENABLED=false
DAYMATE_DATA_DIR=/var/lib/daymate/data
DAYMATE_LOG_FILE=/var/log/daymate/daymate.log
EOF
  chmod 640 "$ENV_FILE"
  chown root:daymate "$ENV_FILE"
fi

install -m 644 /tmp/daymate.service /etc/systemd/system/daymate.service
systemctl daemon-reload
systemctl enable daymate

echo "Server layout is ready. Put the jar at $APP_DIR/daymate-bot-assistant.jar and set TELEGRAM_BOT_TOKEN in $ENV_FILE."
