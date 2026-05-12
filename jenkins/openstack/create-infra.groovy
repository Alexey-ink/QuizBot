pipeline {
    agent { label 'emeshkin' }

    options {
        timeout(time: 45, unit: 'MINUTES')
        timestamps()
        disableConcurrentBuilds()
    }

    environment {
        HEAT_TEMPLATE = 'infrastructure/heat/stack.yaml'
        OS_CREDENTIALS_ID = 'emeshkin-openrc'
    }

    parameters {
        string(name: 'STACK_NAME', defaultValue: 'emeshkin-lab3-stack', description: 'Имя Heat-стека в OpenStack')
        choice(
            name: 'HEAT_ACTION',
            choices: ['update', 'create', 'recreate'],
            description: 'update: openstack stack update; create: stack create (если нет); recreate: delete + create'
        )
        string(name: 'SERVER_NAME', defaultValue: 'emeshkin-bot-vm', description: 'Имя ВМ: если уже есть — reuse; иначе создаётся новая (ниже параметры)')
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'Имя образа (только если ВМ создаём заново)')
        string(name: 'FLAVOR_NAME', defaultValue: '', description: 'Имя flavor (только если ВМ создаём заново)')
        string(name: 'NETWORK_ID', defaultValue: '', description: 'UUID сети --nic net-id=... (только если ВМ создаём заново)')
        string(name: 'KEY_PAIR', defaultValue: '', description: 'Имя keypair в OpenStack (только если ВМ создаём заново)')
        string(name: 'SECURITY_GROUP', defaultValue: 'default', description: 'Security group для новой ВМ')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    if (!fileExists(env.HEAT_TEMPLATE)) {
                        error "❌ Heat-шаблон не найден: ${HEAT_TEMPLATE}"
                    }
                }
            }
        }

        stage('Load OpenStack Credentials') {
            steps {
                script {
                    loadSecretsIntoEnv("${OS_CREDENTIALS_ID}")
                }
                sh '''
                    set +x
                    openstack token issue -f yaml | head -5
                '''
            }
        }

        stage('Resolve VM (reuse or create)') {
            steps {
                sh '''
                    set -euo pipefail
                    SERVER_NAME_TRIMMED="$(echo "${SERVER_NAME}" | xargs)"
                    test -n "${SERVER_NAME_TRIMMED}"

                    HEAT_ENV="${WORKSPACE}/heat-lab3-params.yaml"
                    EXISTING_ID="$(openstack server list --name "^${SERVER_NAME_TRIMMED}$" -f value -c ID 2>/dev/null | head -n 1 || true)"

                    if [ -n "${EXISTING_ID}" ]; then
                      echo "✅ ЛР3: найдена существующая ВМ ${SERVER_NAME_TRIMMED} (${EXISTING_ID})"
                      SERVER_ID="${EXISTING_ID}"
                      SERVER_IP="$(openstack server show "${SERVER_ID}" -f value -c addresses | sed -E 's/.*=([^, ]+).*/\\1/' || true)"
                      test -n "${SERVER_IP}"
                    else
                      echo "ℹ️ ЛР3: ВМ нет — создаём ${SERVER_NAME_TRIMMED}"
                      test -n "${IMAGE_NAME}"
                      test -n "${FLAVOR_NAME}"
                      test -n "${NETWORK_ID}"
                      test -n "${KEY_PAIR}"
                      openstack server create "${SERVER_NAME_TRIMMED}" \
                        --image "${IMAGE_NAME}" \
                        --flavor "${FLAVOR_NAME}" \
                        --nic "net-id=${NETWORK_ID}" \
                        --key-name "${KEY_PAIR}" \
                        ${SECURITY_GROUP:+--security-group "${SECURITY_GROUP}"} \
                        --wait
                      SERVER_ID="$(openstack server show "${SERVER_NAME_TRIMMED}" -f value -c id)"
                      SERVER_IP="$(openstack server show "${SERVER_ID}" -f value -c addresses | sed -E 's/.*=([^, ]+).*/\\1/' || true)"
                      test -n "${SERVER_IP}"
                    fi

                    cat > "${HEAT_ENV}" <<EOF
parameters:
  existing_server_id: "${SERVER_ID}"
  existing_server_name: "${SERVER_NAME_TRIMMED}"
  existing_server_private_ip: "${SERVER_IP}"
EOF
                    echo "📝 Heat environment:"
                    sed 's/existing_server_id:.*/existing_server_id: ***/' "${HEAT_ENV}"
                '''
            }
        }

        stage('Apply Heat stack') {
            steps {
                script {
                    def stack = params.STACK_NAME.trim()
                    if (!stack) {
                        error('❌ STACK_NAME пустой')
                    }
                    env.EFFECTIVE_STACK_NAME = stack

                    def status = sh(
                        script: "openstack stack show ${stack} -f value -c stack_status 2>/dev/null || echo NOT_FOUND",
                        returnStdout: true
                    ).trim()

                    echo "ℹ️ Текущий статус стека '${stack}': ${status}"

                    if (params.HEAT_ACTION == 'recreate' && status != 'NOT_FOUND') {
                        sh """
                            set -euo pipefail
                            openstack stack delete --yes ${stack}
                            for i in {1..60}; do
                              s=\$(openstack stack show ${stack} -f value -c stack_status 2>/dev/null || echo DELETED)
                              if [ "\$s" = "DELETED" ] || [ "\$s" = "" ]; then
                                echo "✅ Стек удалён"
                                break
                              fi
                              echo "⏳ Ждём удаление... (\$i/60) статус=\$s"
                              sleep 5
                            done
                        """
                    }

                    if (params.HEAT_ACTION == 'create' || params.HEAT_ACTION == 'recreate') {
                        sh """
                            set -euo pipefail
                            openstack stack create -e "\${WORKSPACE}/heat-lab3-params.yaml" -t ${HEAT_TEMPLATE} --wait ${stack}
                        """
                    } else { // update
                        if (status == 'NOT_FOUND') {
                            sh """
                                set -euo pipefail
                                openstack stack create -e "\${WORKSPACE}/heat-lab3-params.yaml" -t ${HEAT_TEMPLATE} --wait ${stack}
                            """
                        } else {
                            sh """
                                set -euo pipefail
                                openstack stack update -e "\${WORKSPACE}/heat-lab3-params.yaml" -t ${HEAT_TEMPLATE} --wait ${stack}
                            """
                        }
                    }
                }
            }
        }

        stage('Collect outputs') {
            steps {
                script {
                    def stack = env.EFFECTIVE_STACK_NAME

                    env.SERVER_IP = sh(
                        script: "openstack stack output show -c output_value -f value ${stack} server_private_ip",
                        returnStdout: true
                    ).trim()

                    if (!env.SERVER_IP) {
                        error("❌ Не удалось получить output server_private_ip для стека ${stack}")
                    }

                    sh """
                        set -euo pipefail
                        openstack stack output show --all --format json ${stack} > stack_outputs.json
                    """

                    archiveArtifacts artifacts: 'stack_outputs.json', allowEmptyArchive: false
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "🎉 Heat stack OK: ${env.EFFECTIVE_STACK_NAME} → ${env.SERVER_IP}"
        }
    }
}

def loadSecretsIntoEnv(String credentialId) {
    withCredentials([file(credentialsId: credentialId, variable: 'OPENRC_FILE')]) {
        def content = readFile file: OPENRC_FILE, encoding: 'UTF-8'

        content.split('\n').each { rawLine ->
            try {
                def line = rawLine.trim()
                if (!line || line.startsWith('#')) return

                if (line.startsWith('export ')) {
                    line = line.substring(7).trim()
                }

                def parts = line.split('=', 2)
                if (parts.length != 2) return

                def key = parts[0].trim()
                def value = parts[1].trim()

                while (value.length() >= 2 &&
                        ((value.startsWith('"') && value.endsWith('"')) ||
                         (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1)
                }
                value = value.trim()

                env."${key}" = value

                if (key == 'OS_PASSWORD') {
                    echo "✅ Loaded: ${key}=***"
                } else {
                    echo "✅ Loaded: ${key}=${value}"
                }
            } catch (Exception e) {
                echo "❌ Error parsing openrc line: ${e.message}"
            }
        }
    }
}
