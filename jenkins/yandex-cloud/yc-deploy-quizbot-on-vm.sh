#!/usr/bin/env bash
# Деплой QuizBot на ВМ Yandex Cloud (аналог lab5-deploy-quizbot-on-vm.sh для OpenStack).
# Без TELEGRAM_BOT_TOKEN в env register-on-startup принудительно false (как env в k8s/deployment.yaml, ЛР7).
# В консоли YC SSH: ssh -l emeshkin <публичный_IP> → здесь SSH_USER=emeshkin по умолчанию.
#
# Запуск с машины, где есть репозиторий и ключ:
#   export WORKSPACE=/path/to/QuizBot
#   export SSH_KEY_PATH=~/.ssh/id_ed25519
#   export VM_IP=81.26.183.246
#   export SSH_USER=emeshkin   # опционально
#   export QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP=true   # только вместе с непустым TELEGRAM_BOT_TOKEN
#   export TELEGRAM_BOT_TOKEN=...   # как в ЛР7/k8s: если пусто — register-on-startup принудительно false
#   bash jenkins/yandex-cloud/yc-deploy-quizbot-on-vm.sh
#
# JAR: quizbot-app-*.jar в $WORKSPACE/build/libs или где угодно под $WORKSPACE (например build-artifacts-jar/).

set -eu

: "${WORKSPACE:?Задай WORKSPACE=корень репозитория QuizBot}"
: "${VM_IP:?Задай VM_IP=публичный IPv4 ВМ Yandex Cloud}"
: "${SSH_KEY_PATH:?Задай SSH_KEY_PATH=приватный ключ для SSH}"

SSH_USER="${SSH_USER:-emeshkin}"
DB_NAME="${DB_NAME:-quizbot}"
DB_USER="${DB_USER:-quizbot}"
DB_PASS="${DB_PASS:-quizbot_pass}"
QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP="${QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP:-false}"
TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN:-}"
TELEGRAM_BOT_TOKEN="${TELEGRAM_BOT_TOKEN//$'\r'/}"
# Как в k8s/deployment.yaml (ЛР7): без токена registerBot не вызываем — иначе Spring падает до Jetty/healthcheck.
if [ -z "${TELEGRAM_BOT_TOKEN}" ]; then
  QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP=false
fi
TELEGRAM_BOT_USERNAME="${TELEGRAM_BOT_USERNAME:-@tlgrm_quiz_bot}"
TELEGRAM_BOT_USERNAME="${TELEGRAM_BOT_USERNAME//$'\r'/}"

TARGET="${SSH_USER}@${VM_IP}"
SSH=(ssh -i "${SSH_KEY_PATH}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)
SCP=(scp -i "${SSH_KEY_PATH}" -o StrictHostKeyChecking=accept-new -o UserKnownHostsFile=/dev/null)

chmod 600 "${SSH_KEY_PATH}"

echo ">>> Каталог приложения и владелец ${SSH_USER} на ${TARGET}..."
"${SSH[@]}" "${TARGET}" "sudo mkdir -p /opt/quizbot && sudo chown -R '${SSH_USER}:${SSH_USER}' /opt/quizbot"

echo ">>> PostgreSQL + Temurin 23 на ${TARGET}..."
"${SSH[@]}" "${TARGET}" bash -s <<REMOTE
set -eu
export DEBIAN_FRONTEND=noninteractive

sudo apt-get update -y
sudo apt-get install -y \
  ca-certificates wget gnupg postgresql postgresql-contrib python3 python3-pip

sudo install -d -m 0755 /etc/apt/keyrings
wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | sudo gpg --batch --no-tty --dearmor -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb \$(. /etc/os-release && echo \$VERSION_CODENAME) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null

sudo apt-get update -y
sudo apt-get install -y temurin-23-jdk

sudo systemctl enable --now postgresql

sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1 || \
  sudo -u postgres psql -c "CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASS}';"

sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 || \
  sudo -u postgres createdb -O "${DB_USER}" "${DB_NAME}"

java -version
REMOTE

UNIT_SRC="${WORKSPACE}/deploy/systemd/quizbot.service"
test -f "${UNIT_SRC}" || {
  echo "Нет unit: ${UNIT_SRC}" >&2
  exit 1
}

JAR="$(find "${WORKSPACE}/build/libs" -maxdepth 1 -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -n 1 || true)"
if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
  JAR="$(find "${WORKSPACE}" -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -n 1 || true)"
fi
test -f "${JAR}" || {
  echo "Нет quizbot-app-*.jar под WORKSPACE=${WORKSPACE}" >&2
  exit 1
}

UNIT_TMP="$(mktemp)"
trap 'rm -f "${UNIT_TMP}"' EXIT
# В Yandex логин по SSH обычно не ubuntu — подставляем пользователя ВМ в unit.
sed -e "s/^User=.*/User=${SSH_USER}/" -e "s/^Group=.*/Group=${SSH_USER}/" "${UNIT_SRC}" > "${UNIT_TMP}"

echo ">>> Копирую JAR и unit..."
"${SCP[@]}" "${UNIT_TMP}" "${TARGET}:/tmp/quizbot.service"
"${SCP[@]}" "${JAR}" "${TARGET}:/opt/quizbot/quizbot.jar"
"${SSH[@]}" "${TARGET}" "sudo chown '${SSH_USER}:${SSH_USER}' /opt/quizbot/quizbot.jar"

echo ">>> quizbot.env + systemd..."
"${SSH[@]}" "${TARGET}" bash -s <<REMOTE2
set -eu

sudo tee /opt/quizbot/quizbot.env >/dev/null <<ENVEOF
POSTGRES_USER=${DB_USER}
POSTGRES_PASSWORD=${DB_PASS}
POSTGRES_URL=jdbc:postgresql://127.0.0.1:5432/${DB_NAME}
QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP=${QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP}
TELEGRAM_BOT_TOKEN=${TELEGRAM_BOT_TOKEN}
TELEGRAM_BOT_USERNAME=${TELEGRAM_BOT_USERNAME}
ENVEOF
sudo chown root:root /opt/quizbot/quizbot.env
sudo chmod 600 /opt/quizbot/quizbot.env
sudo install -m 0644 /tmp/quizbot.service /etc/systemd/system/quizbot.service
sudo systemctl daemon-reload
sudo systemctl enable quizbot.service
sudo systemctl restart quizbot.service
sudo systemctl is-active --quiet quizbot.service
REMOTE2

echo ">>> Готово: quizbot.service на ${VM_IP}. Проверка: curl -sS http://${VM_IP}:8080/healthcheck"
