// Деплой QuizBot на ВМ Yandex Cloud (отдельный job).
// Скрипт: jenkins/yandex-cloud/yc-deploy-quizbot-on-vm.sh (токен Telegram в job не задаём — только HTTP/healthcheck на ВМ).
// Нужны: Jenkins credential «SSH Username with private key» (см. SSH_CREDENTIALS_ID), copyArtifacts из JOB_BUILD_JAR.

pipeline {
    agent { label 'emeshkin' }

    options {
        timeout(time: 35, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    parameters {
        string(
            name: 'VM_IP',
            defaultValue: '81.26.183.246',
            description: 'Публичный IPv4 ВМ Yandex Cloud'
        )
        string(
            name: 'SSH_USER',
            defaultValue: 'emeshkin',
            description: 'Пользователь SSH (как в консоли YC: ssh -l …)'
        )
        string(
            name: 'SSH_CREDENTIALS_ID',
            defaultValue: 'emeshkin-ssh-yandex',
            description: 'Jenkins credential ID: приватный ключ для SSH на ВМ (тот же, что в authorized_keys пользователя SSH_USER)'
        )
        string(
            name: 'JOB_BUILD_JAR',
            defaultValue: 'java-build',
            description: 'Job, откуда copyArtifacts: build/libs/quizbot-app-*.jar'
        )
        string(name: 'DB_NAME', defaultValue: 'quizbot', description: 'Имя БД')
        string(name: 'DB_USER', defaultValue: 'quizbot', description: 'Пользователь БД')
        string(name: 'DB_PASS', defaultValue: 'quizbot_pass', description: 'Пароль БД')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Copy JAR from build job') {
            steps {
                script {
                    def jobName = (params.JOB_BUILD_JAR ?: 'java-build').toString().trim()
                    echo "📦 copyArtifacts from: ${jobName}"
                    copyArtifacts(
                        projectName: jobName,
                        selector: lastSuccessful(),
                        target: 'build-artifacts-jar',
                        filter: 'build/libs/quizbot-app-*.jar',
                        fingerprintArtifacts: true
                    )
                    def found = sh(
                        script: "find \"${env.WORKSPACE}/build-artifacts-jar\" -type f -name 'quizbot-app-*.jar' 2>/dev/null | head -1",
                        returnStdout: true
                    ).trim()
                    if (!found) {
                        error("❌ Нет quizbot-app-*.jar. Проверь архив в «${jobName}» и Permission to copy artifact для этого job.")
                    }
                    echo "✅ JAR: ${found}"
                }
            }
        }

        stage('Deploy to Yandex VM') {
            steps {
                script {
                    def credId = (params.SSH_CREDENTIALS_ID ?: 'emeshkin-ssh-yandex').toString().trim()
                    echo "🔑 SSH credential для ВМ: ${credId}"

                    withCredentials([
                        sshUserPrivateKey(
                            credentialsId: credId,
                            keyFileVariable: 'SSH_KEY_PATH',
                            usernameVariable: 'SSH_USER_FROM_CRED'
                        )
                    ]) {
                        def vmIp = (params.VM_IP ?: '81.26.183.246').toString().trim()
                        def sshUser = (params.SSH_USER ?: 'emeshkin').toString().trim()
                        def dbName = (params.DB_NAME ?: 'quizbot').toString()
                        def dbUser = (params.DB_USER ?: 'quizbot').toString()
                        def dbPass = (params.DB_PASS ?: 'quizbot_pass').toString()

                        withEnv([
                            "VM_IP=${vmIp}",
                            "SSH_USER=${sshUser}",
                            "DB_NAME=${dbName}",
                            "DB_USER=${dbUser}",
                            "DB_PASS=${dbPass}",
                            "TELEGRAM_BOT_TOKEN=",
                            "TELEGRAM_BOT_USERNAME=@tlgrm_quiz_bot",
                            "QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP=false",
                        ]) {
                            // sh ''' ломал редирект sed на агенте (пустой KEYFIX). Здесь GString + \\$ — путь к ключу только в shell.
                            sh """
                                set -eu
                                KEYFIX='${env.WORKSPACE}/.ssh_key_for_yc_deploy'
                                SRC_BYTES=\$(wc -c <"\$SSH_KEY_PATH" | tr -d ' ')
                                if [ "\${SRC_BYTES}" -eq 0 ]; then
                                    echo '❌ В credential пустой Private Key (0 байт). Открой SSH credential → вставь id_ed25519 целиком → Save.' >&2
                                    exit 1
                                fi
                                CR=\$(printf '\\r')
                                tr -d "\${CR}" < "\$SSH_KEY_PATH" > "\${KEYFIX}"
                                chmod 600 "\${KEYFIX}"
                                LINE1=\$(head -n 1 "\${KEYFIX}" | tr -d "\${CR}" || true)
                                if ! printf '%s\\n' "\${LINE1}" | grep -qE '^-----BEGIN.*PRIVATE KEY-----'; then
                                    echo '❌ В Private Key не PEM/OpenSSH приватный ключ (нужен id_ed25519, не .pub).' >&2
                                    echo "   Первые 16 байт (hex): \$(head -c 16 "\${KEYFIX}" | od -An -tx1)" >&2
                                    exit 1
                                fi
                                export SSH_KEY_PATH="\${KEYFIX}"
                                bash '${env.WORKSPACE}/jenkins/yandex-cloud/yc-deploy-quizbot-on-vm.sh'
                                rm -f "\${KEYFIX}"
                            """
                        }
                    }
                }
            }
        }

        stage('Healthcheck (optional)') {
            steps {
                script {
                    def ip = params.VM_IP.toString().trim()
                    sh """
                        set +e
                        curl -sS --connect-timeout 8 "http://${ip}:8080/healthcheck" | head -c 400
                        echo
                        exit 0
                    """
                }
            }
        }
    }

    post {
        always {
            sh "rm -f '${env.WORKSPACE}/.ssh_key_for_yc_deploy' 2>/dev/null || true"
        }
    }
}
