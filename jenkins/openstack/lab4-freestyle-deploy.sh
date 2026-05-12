#!/usr/bin/env bash
# ЛР4: выкладка JAR + systemd unit (как openstack/deploy.groovy).
# В Jenkins: Execute shell, WORKSPACE из окружения; задайте APP_HOST или VM_IP.

set -eu

: "${WORKSPACE:?WORKSPACE не задан}"

SSH_KEY="${SSH_KEY:-/home/ubuntu/.ssh/emeshkin-bot.pem}"
test -f "$SSH_KEY"

if [ -n "${APP_HOST:-}" ]; then
  TARGET="$APP_HOST"
elif [ -n "${VM_IP:-}" ]; then
  TARGET="ubuntu@${VM_IP}"
else
  echo "Задайте APP_HOST (например ubuntu@192.168.24.227) или VM_IP (только IPv4)." >&2
  exit 1
fi

UNIT_SRC="${WORKSPACE}/deploy/systemd/quizbot.service"
test -f "$UNIT_SRC" || {
  echo "Нет файла unit: ${UNIT_SRC}" >&2
  exit 1
}

JAR="$(find "${WORKSPACE}/build/libs" -maxdepth 1 -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -n 1 || true)"
if [ -z "$JAR" ] || [ ! -f "$JAR" ]; then
  JAR="$(find "${WORKSPACE}" -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -n 1 || true)"
fi
test -n "$JAR" && test -f "$JAR" || {
  echo "Не найден shadow-JAR quizbot-app-*.jar (сначала ./gradlew shadowJar или стадия build)." >&2
  exit 1
}

SSH=(ssh -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new)
SCP=(scp -i "$SSH_KEY" -o StrictHostKeyChecking=accept-new)

"${SSH[@]}" "$TARGET" "sudo mkdir -p /opt/quizbot && sudo chown -R ubuntu:ubuntu /opt/quizbot"

"${SCP[@]}" "$UNIT_SRC" "${TARGET}:/tmp/quizbot.service"
"${SCP[@]}" "$JAR" "${TARGET}:/opt/quizbot/quizbot.jar"

"${SSH[@]}" "$TARGET" "
  set -eu
  sudo install -m 0644 /tmp/quizbot.service /etc/systemd/system/quizbot.service
  sudo systemctl daemon-reload
  sudo systemctl enable quizbot.service
  sudo systemctl restart quizbot.service
  sudo systemctl is-active --quiet quizbot.service
"

echo "OK: ${TARGET} quizbot.service active, JAR=/opt/quizbot/quizbot.jar"
