#!/usr/bin/env bash
# ЛР4 (Freestyle): тонкий деплой на уже подготовленную ВМ — JAR + quizbot.env + systemd (как хвост lab5, без apt/postgres).
# Новая логика: пустой TELEGRAM_BOT_TOKEN → QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP=false (как k8s ЛР7).
#
# Jenkins:
#   1) Copy artifacts из java-build: build/libs/quizbot-app-*.jar (и flatten при необходимости).
#   2) Репозиторий: Git на ту же ветку ИЛИ второй шаг Copy Artifacts с deploy/systemd/quizbot.service из артефакта.
#   3) Execute shell:
#        export VM_IP=192.168.24.227
#        export SSH_USER=ubuntu
#        export SSH_KEY_PATH=/home/ubuntu/.ssh/emeshkin-bot.pem
#        bash "${WORKSPACE}/jenkins/openstack/lab4-freestyle-deploy.sh"
#      Либо: export APP_HOST=ubuntu@192.168.24.227

set -eu

: "${WORKSPACE:?WORKSPACE не задан}"

SSH_KEY_PATH="${SSH_KEY_PATH:-${SSH_KEY:-${HOME}/.ssh/emeshkin-bot.pem}}"
test -f "${SSH_KEY_PATH}"
chmod 600 "${SSH_KEY_PATH}" 2>/dev/null || true

SSH_USER="${SSH_USER:-ubuntu}"
DB_NAME="${DB_NAME:-quizbot}"
DB_USER="${DB_USER:-quizbot}"
DB_PASS="${DB_PASS:-quizbot_pass}"
QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP="${QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP:-false}"
TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"
TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN//$'\r'/}"
if [ -z "${TELEGRAM_BOT_TOKEN}" ]; then
  QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP=false
fi
TELEGRAM_BOT_USERNAME="${TELEGRAM_BOT_USERNAME:-@tlgrm_quiz_bot}"
TELEGRAM_BOT_USERNAME="${TELEGRAM_BOT_USERNAME//$'\r'/}"

if [ -n "${APP_HOST:-}" ]; then
  TARGET="${APP_HOST}"
elif [ -n "${VM_IP:-}" ]; then
  TARGET="${SSH_USER}@${VM_IP}"
else
  echo "Задайте APP_HOST (ubuntu@192.168.x.x) или VM_IP (+ опционально SSH_USER)." >&2
  exit 1
fi

REMOTE_USER="${TARGET%%@*}"

UNIT_SRC="${WORKSPACE}/deploy/systemd/quizbot.service"
test -f "${UNIT_SRC}" || {
  echo "Нет unit: ${UNIT_SRC} — включи Git в job или добавь артефакт с deploy/systemd." >&2
  exit 1
}

JAR="$(find "${WORKSPACE}/build/libs" -maxdepth 1 -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -n 1 || true)"
if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
  JAR="$(find "${WORKSPACE}" -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -n 1 || true)"
fi
test -f "${JAR}" || {
  echo "Не найден quizbot-app-*.jar. В Copy Artifacts укажи: build/libs/quizbot-app-*.jar" >&2
  exit 1
}

SSH=(ssh -i "${SSH_KEY_PATH}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)
SCP=(scp -i "${SSH_KEY_PATH}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)

echo ">>> Каталог /opt/quizbot и владелец ${REMOTE_USER} на ${TARGET}..."
"${SSH[@]}" "${TARGET}" "sudo mkdir -p /opt/quizbot && sudo chown -R '${REMOTE_USER}:${REMOTE_USER}' /opt/quizbot"

echo ">>> Копирую unit и JAR..."
UNIT_TMP="$(mktemp)"
trap 'rm -f "${UNIT_TMP}"' EXIT
sed -e "s/^User=.*/User=${REMOTE_USER}/" -e "s/^Group=.*/Group=${REMOTE_USER}/" "${UNIT_SRC}" > "${UNIT_TMP}"
"${SCP[@]}" "${UNIT_TMP}" "${TARGET}:/tmp/quizbot.service"
"${SCP[@]}" "${JAR}" "${TARGET}:/opt/quizbot/quizbot.jar"
"${SSH[@]}" "${TARGET}" "sudo chown '${REMOTE_USER}:${REMOTE_USER}' /opt/quizbot/quizbot.jar"

echo ">>> quizbot.env + systemd..."
"${SSH[@]}" "${TARGET}" "sudo tee /opt/quizbot/quizbot.env >/dev/null" <<ENVFILE
POSTGRES_USER=${DB_USER}
POSTGRES_PASSWORD=${DB_PASS}
POSTGRES_URL=jdbc:postgresql://127.0.0.1:5432/${DB_NAME}
QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP=${QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP}
TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
TELEGRAM_BOT_USERNAME=${TELEGRAM_BOT_USERNAME}
ENVFILE

"${SSH[@]}" "${TARGET}" bash -s <<'REMOTE'
set -eu
sudo chown root:root /opt/quizbot/quizbot.env
sudo chmod 600 /opt/quizbot/quizbot.env
sudo install -m 0644 /tmp/quizbot.service /etc/systemd/system/quizbot.service
sudo rm -f /tmp/quizbot.service
sudo systemctl daemon-reload
sudo systemctl enable quizbot.service
sudo systemctl restart quizbot.service
sudo systemctl is-active --quiet quizbot.service
REMOTE

echo ">>> Готово: ${TARGET} — quizbot.service active. Проверка: curl -sS http://127.0.0.1:8080/healthcheck (на ВМ)."
