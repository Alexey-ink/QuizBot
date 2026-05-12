// Деплой QuizBot на ВМ Yandex Cloud (отдельный job).
// Скрипт: jenkins/yandex-cloud/yc-deploy-quizbot-on-vm.sh
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
        booleanParam(
            name: 'QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP',
            defaultValue: false,
            description: 'true — registerBot при старте (нужен доступ к api.telegram.org)'
        )
        string(
            name: 'TELEGRAM_BOT_TOKEN',
            defaultValue: '',
            description: 'Опционально. Для секрета лучше отдельный Jenkins credential и доработка pipeline.'
        )
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
                        def qtr = (params.QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP == true) ? 'true' : 'false'
                        def dbName = (params.DB_NAME ?: 'quizbot').toString()
                        def dbUser = (params.DB_USER ?: 'quizbot').toString()
                        def dbPass = (params.DB_PASS ?: 'quizbot_pass').toString()
                        def tok = (params.TELEGRAM_BOT_TOKEN ?: '').toString()

                        writeFile encoding: 'UTF-8', file: "${env.WORKSPACE}/.telegram_token_for_yc_deploy", text: tok

                        withEnv([
                            "VM_IP=${vmIp}",
                            "SSH_USER=${sshUser}",
                            "DB_NAME=${dbName}",
                            "DB_USER=${dbUser}",
                            "DB_PASS=${dbPass}",
                            "QUIZBOT_TELEGRAM_REGISTER_ON_STARTUP=${qtr}",
                        ]) {
                            sh '''
                                set -eu
                                chmod 600 "${SSH_KEY_PATH}"
                                export TELEGRAM_BOT_TOKEN="$(tr -d "\\r" < "${WORKSPACE}/.telegram_token_for_yc_deploy" || true)"
                                bash "${WORKSPACE}/jenkins/yandex-cloud/yc-deploy-quizbot-on-vm.sh"
                                rm -f "${WORKSPACE}/.telegram_token_for_yc_deploy"
                            '''
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
            sh "rm -f '${env.WORKSPACE}/.telegram_token_for_yc_deploy' 2>/dev/null || true"
        }
    }
}
