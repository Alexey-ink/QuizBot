#!/usr/bin/env bash
# ЛР3 (Freestyle): Heat + подготовка ВМ + копирование quizbot.jar и quizbot.service, запуск Java.
# На агенте Jenkins: checkout репо; при необходимости перед шагом выполни ./gradlew shadowJar.

set -eu
cd "${WORKSPACE}"

: "${OS_PASSWORD:?OS_PASSWORD is not set in Jenkins credentials}"

export OS_AUTH_URL="https://cloud.crplab.ru:5000/v3"
export OS_USERNAME="student"
export OS_PROJECT_NAME="student"
export OS_USER_DOMAIN_NAME="Default"
export OS_PROJECT_DOMAIN_NAME="Default"
export OS_IDENTITY_API_VERSION="3"
export OS_REGION_NAME="RegionOne"
export OS_PASSWORD

STACK_NAME="${STACK_NAME:-emeshkin-lab3-stack-v4}"
TEMPLATE="${WORKSPACE}/infrastructure/heat/stack.yaml"
SSH_KEY="/home/ubuntu/.ssh/emeshkin-bot.pem"

VM_NAME="${VM_NAME:-emeshkin-bot-vm-new}"
IMAGE_NAME="${IMAGE_NAME:-}"
FLAVOR_NAME="${FLAVOR_NAME:-}"
NETWORK_ID="${NETWORK_ID:-}"
KEY_PAIR="${KEY_PAIR:-}"
SECURITY_GROUP="${SECURITY_GROUP:-default}"

DB_NAME="${DB_NAME:-quizbot}"
DB_USER="${DB_USER:-quizbot}"
DB_PASS="${DB_PASS:-quizbot_pass}"

test -f "$TEMPLATE"
test -f "$SSH_KEY"

extract_ipv4_from_addresses_json() {
  python3 -c '
import json,sys,re
data=json.load(sys.stdin)
addrs=data.get("addresses", {})
for net in addrs.values():
    for ip in net:
        if re.match(r"^\d+\.\d+\.\d+\.\d+$", ip):
            print(ip); sys.exit(0)
print("")
'
}

VM_NAME_TRIMMED="$(echo "${VM_NAME}" | xargs)"
test -n "${VM_NAME_TRIMMED}"

EXISTING_ID="$(openstack server list --name "^${VM_NAME_TRIMMED}$" -f value -c ID 2>/dev/null | head -n 1 || true)"

if [ -n "${EXISTING_ID}" ]; then
  echo "Используем существующую ВМ: ${VM_NAME_TRIMMED} (${EXISTING_ID})"
  VM_ID="${EXISTING_ID}"
else
  echo "Создаём новую ВМ: ${VM_NAME_TRIMMED}"
  test -n "${IMAGE_NAME}"
  test -n "${FLAVOR_NAME}"
  test -n "${NETWORK_ID}"
  test -n "${KEY_PAIR}"

  openstack server create "${VM_NAME_TRIMMED}" \
    --image "${IMAGE_NAME}" \
    --flavor "${FLAVOR_NAME}" \
    --nic "net-id=${NETWORK_ID}" \
    --key-name "${KEY_PAIR}" \
    ${SECURITY_GROUP:+--security-group "${SECURITY_GROUP}"} \
    --wait

  VM_ID="$(openstack server show "${VM_NAME_TRIMMED}" -f value -c id)"
fi

VM_IP="$(
  openstack server show "${VM_ID}" -f json -c addresses \
  | extract_ipv4_from_addresses_json
)"
test -n "${VM_IP}"

HEAT_ENV="${WORKSPACE}/heat-lab3-params.yaml"
cat > "${HEAT_ENV}" <<EOF
parameters:
  existing_server_id: "${VM_ID}"
  existing_server_name: "${VM_NAME_TRIMMED}"
  existing_server_private_ip: "${VM_IP}"
EOF

if openstack stack show "$STACK_NAME" >/dev/null 2>&1; then
  if ! openstack stack update -e "${HEAT_ENV}" -t "$TEMPLATE" --wait "$STACK_NAME"; then
    openstack stack delete --wait "$STACK_NAME" || true
    openstack stack create -e "${HEAT_ENV}" -t "$TEMPLATE" --wait "$STACK_NAME"
  fi
else
  openstack stack create -e "${HEAT_ENV}" -t "$TEMPLATE" --wait "$STACK_NAME"
fi

VM_IP_FROM_STACK="$(
  openstack stack output show -f value -c output_value "$STACK_NAME" server_private_ip \
  | python3 -c '
import sys,ast,re
s=sys.stdin.read().strip()
if re.match(r"^\d+\.\d+\.\d+\.\d+$", s):
    print(s); raise SystemExit
try:
    d=ast.literal_eval(s)
    if isinstance(d, dict):
        for net in d.values():
            if isinstance(net, list):
                for ip in net:
                    if re.match(r"^\d+\.\d+\.\d+\.\d+$", str(ip)):
                        print(ip); raise SystemExit
except Exception:
    pass
print("")
'
)"
test -n "${VM_IP_FROM_STACK}"
echo "VM_IP_FROM_STACK=${VM_IP_FROM_STACK}"

ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "ubuntu@${VM_IP_FROM_STACK}" "
  set -eu

  sudo apt-get update -y
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y \
    ca-certificates wget gnupg postgresql postgresql-contrib python3 python3-pip

  sudo install -d -m 0755 /etc/apt/keyrings
  wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
    | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
  echo \"deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb \$(. /etc/os-release && echo \$VERSION_CODENAME) main\" \
    | sudo tee /etc/apt/sources.list.d/adoptium.list >/dev/null

  sudo apt-get update -y
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y temurin-23-jdk

  sudo systemctl enable --now postgresql

  sudo -u postgres psql -tc \"SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'\" | grep -q 1 || \
    sudo -u postgres psql -c \"CREATE USER ${DB_USER} WITH PASSWORD '${DB_PASS}';\"

  sudo -u postgres psql -tc \"SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'\" | grep -q 1 || \
    sudo -u postgres createdb -O ${DB_USER} ${DB_NAME}

  java -version
  javac -version

  sudo mkdir -p /opt/quizbot
  sudo chown -R ubuntu:ubuntu /opt/quizbot
"

# --- С агента Jenkins: JAR + unit, иначе /opt/quizbot остаётся пустым ---
UNIT_SRC="${WORKSPACE}/deploy/systemd/quizbot.service"
test -f "${UNIT_SRC}" || {
  echo "Нет unit: ${UNIT_SRC} (нужен checkout репозитория)." >&2
  exit 1
}

JAR="$(find "${WORKSPACE}/build/libs" -maxdepth 1 -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -n 1 || true)"
if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
  JAR="$(find "${WORKSPACE}" -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -n 1 || true)"
fi
if [ -z "${JAR}" ] || [ ! -f "${JAR}" ]; then
  if [ -x "${WORKSPACE}/gradlew" ]; then
    echo "JAR не найден — запускаю ./gradlew shadowJar ..."
    (cd "${WORKSPACE}" && ./gradlew --no-daemon shadowJar)
    JAR="$(find "${WORKSPACE}/build/libs" -maxdepth 1 -type f -name 'quizbot-app-*.jar' | head -n 1)"
  fi
fi
test -f "${JAR}" || {
  echo "Не найден quizbot-app-*.jar. Добавь в job шаг сборки или положи JAR в WORKSPACE." >&2
  exit 1
}

APP_TARGET="ubuntu@${VM_IP_FROM_STACK}"
scp -i "$SSH_KEY" -o StrictHostKeyChecking=no "${UNIT_SRC}" "${APP_TARGET}:/tmp/quizbot.service"
scp -i "$SSH_KEY" -o StrictHostKeyChecking=no "${JAR}" "${APP_TARGET}:/opt/quizbot/quizbot.jar"

ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no "${APP_TARGET}" bash <<REMOTE
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
REMOTE

echo "Готово: quizbot.service активен, JAR=/opt/quizbot/quizbot.jar на ${VM_IP_FROM_STACK}"
