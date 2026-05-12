#!/usr/bin/env bash
# После Ansible (ЛР5): как ЛР3 — PostgreSQL + Java на ВМ, JAR + systemd quizbot.service.
# Ожидает: WORKSPACE, VM_IP, SSH_KEY_PATH, SSH_USER; опционально DB_NAME, DB_USER, DB_PASS.

set -eu

: "${WORKSPACE:?WORKSPACE is required}"
: "${VM_IP:?VM_IP is required}"
: "${SSH_KEY_PATH:?SSH_KEY_PATH is required}"

SSH_USER="${SSH_USER:-ubuntu}"
DB_NAME="${DB_NAME:-quizbot}"
DB_USER="${DB_USER:-quizbot}"
DB_PASS="${DB_PASS:-quizbot_pass}"

TARGET="${SSH_USER}@${VM_IP}"
SSH=(ssh -i "${SSH_KEY_PATH}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null)
SCP=(scp -i "${SSH_KEY_PATH}" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null)

chmod 600 "${SSH_KEY_PATH}"

echo ">>> Устанавливаю PostgreSQL + JDK на ${TARGET} (как ЛР3)..."
# Переменные БД подставляются здесь (на агенте), чтобы на ВМ ушли готовые SQL-строки.
"${SSH[@]}" "${TARGET}" bash -s <<REMOTE
set -eu
export DEBIAN_FRONTEND=noninteractive

sudo apt-get update -y
sudo apt-get install -y \
  ca-certificates wget gnupg postgresql postgresql-contrib python3 python3-pip

sudo install -d -m 0755 /etc/apt/keyrings
wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
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
if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
  if [ -x "${WORKSPACE}/gradlew" ]; then
    echo "JAR не найден — ./gradlew shadowJar ..."
    (cd "${WORKSPACE}" && ./gradlew --no-daemon shadowJar)
    JAR="$(find "${WORKSPACE}/build/libs" -maxdepth 1 -type f -name 'quizbot-app-*.jar' | head -n 1)"
  fi
fi
test -f "${JAR}" || {
  echo "Не найден quizbot-app-*.jar" >&2
  exit 1
}

echo ">>> Копирую JAR и unit на ${TARGET}..."
"${SCP[@]}" "${UNIT_SRC}" "${TARGET}:/tmp/quizbot.service"
"${SCP[@]}" "${JAR}" "${TARGET}:/opt/quizbot/quizbot.jar"

echo ">>> quizbot.env + systemd..."
"${SSH[@]}" "${TARGET}" bash -s <<REMOTE2
set -eu

cat <<ENVEOF | sudo tee /opt/quizbot/quizbot.env >/dev/null
POSTGRES_USER=${DB_USER}
POSTGRES_PASSWORD=${DB_PASS}
POSTGRES_URL=jdbc:postgresql://127.0.0.1:5432/${DB_NAME}
ENVEOF
sudo chown root:root /opt/quizbot/quizbot.env
sudo chmod 600 /opt/quizbot/quizbot.env
sudo install -m 0644 /tmp/quizbot.service /etc/systemd/system/quizbot.service
sudo systemctl daemon-reload
sudo systemctl enable quizbot.service
sudo systemctl restart quizbot.service
sudo systemctl is-active --quiet quizbot.service
REMOTE2

echo ">>> Готово: quizbot.service active на ${VM_IP}"
