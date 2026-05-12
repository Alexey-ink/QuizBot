pipeline {
    agent { label 'emeshkin' }

    environment {
        // Jenkins credential type: "Secret file" with openrc OR your student openrc script
        OS_CREDENTIALS_ID = 'emeshkin-openrc'

        // Jenkins credential type: "SSH Username with private key" (private key for VM access)
        SSH_CREDENTIALS_ID = 'emeshkin-bot-ssh'

        INFRA_ARTIFACT_JOB = 'openstack/create-infra'
        BUILD_ARTIFACT_JOB = 'openstack/build'

        VM_USER = 'ubuntu'
        REMOTE_APP_DIR = '/opt/quizbot'
        REMOTE_JAR_NAME = 'quizbot.jar'
        SYSTEMD_UNIT = 'quizbot.service'
        SYSTEMD_UNIT_FILE = 'deploy/systemd/quizbot.service'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                script {
                    if (!fileExists("${env.SYSTEMD_UNIT_FILE}")) {
                        error("❌ Не найден unit: ${env.SYSTEMD_UNIT_FILE}")
                    }
                }
            }
        }

        stage('Prepare OpenStack Env') {
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

        stage('Get VM IP from Infra Job') {
            steps {
                script {
                    copyArtifacts projectName: INFRA_ARTIFACT_JOB,
                            filter: 'stack_outputs.json',
                            target: '.',
                            selector: lastSuccessful(),
                            flatten: true

                    def outputs = readJSON file: 'stack_outputs.json'
                    def serverPrivateIp = outputs.find { it.output_key == 'server_private_ip' }
                    if (!serverPrivateIp) {
                        error("❌ Не найден output 'server_private_ip' в stack_outputs.json")
                    }
                    env.VM_IP = serverPrivateIp.output_value.toString().trim()
                    echo "✅ VM IP: ${env.VM_IP}"
                }
            }
        }

        stage('Copy JAR from Build Job') {
            steps {
                script {
                    copyArtifacts projectName: BUILD_ARTIFACT_JOB,
                            filter: '**/build/libs/*.jar',
                            target: 'artifacts',
                            selector: lastSuccessful(),
                            flatten: true

                    def jars = findFiles glob: 'artifacts/*.jar'
                    if (!jars || jars.length == 0) {
                        error("❌ Не найден JAR в артефактах job '${BUILD_ARTIFACT_JOB}' (ожидалось **/build/libs/*.jar)")
                    }
                    // Берём самый свежий по времени модификации (на случай нескольких jar)
                    def picked = jars.max { f -> new File(f.path).lastModified() }
                    env.LOCAL_JAR = picked.path
                    echo "✅ Выбран JAR: ${env.LOCAL_JAR}"
                }
            }
        }

        stage('Deploy JAR over SSH') {
            steps {
                sshagent(["${SSH_CREDENTIALS_ID}"]) {
                    sh '''
                        set -euo pipefail
                        ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
                          "${VM_USER}@${VM_IP}" "sudo mkdir -p '${REMOTE_APP_DIR}' && sudo chown -R ${VM_USER}:${VM_USER} '${REMOTE_APP_DIR}'"

                        scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
                          "${SYSTEMD_UNIT_FILE}" "${VM_USER}@${VM_IP}:/tmp/${SYSTEMD_UNIT}"

                        scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
                          "${LOCAL_JAR}" "${VM_USER}@${VM_IP}:${REMOTE_APP_DIR}/${REMOTE_JAR_NAME}"

                        ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
                          "${VM_USER}@${VM_IP}" "sudo install -m 0644 /tmp/${SYSTEMD_UNIT} /etc/systemd/system/${SYSTEMD_UNIT} && sudo systemctl daemon-reload && sudo systemctl enable '${SYSTEMD_UNIT}'"

                        ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null \
                          "${VM_USER}@${VM_IP}" "sudo systemctl restart '${SYSTEMD_UNIT}' && sudo systemctl --no-pager --full status '${SYSTEMD_UNIT}' | head -n 40"
                    '''
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        failure {
            echo "❌ Deploy failed: ${env.BUILD_URL}"
        }
        success {
            echo "✅ Deploy OK: ${env.VM_USER}@${env.VM_IP}:${env.REMOTE_APP_DIR}/${env.REMOTE_JAR_NAME}"
        }
    }
}

// Загрузка openrc из FILE-credential в env.* переменные
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
