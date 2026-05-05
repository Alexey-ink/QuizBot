pipeline {
    agent { label 'emeshkin' }

    options {
        timeout(time: 20, unit: 'MINUTES')
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
                            openstack stack create -t ${HEAT_TEMPLATE} --wait ${stack}
                        """
                    } else { // update
                        if (status == 'NOT_FOUND') {
                            sh """
                                set -euo pipefail
                                openstack stack create -t ${HEAT_TEMPLATE} --wait ${stack}
                            """
                        } else {
                            sh """
                                set -euo pipefail
                                openstack stack update -t ${HEAT_TEMPLATE} --wait ${stack}
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
