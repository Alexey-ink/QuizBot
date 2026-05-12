// ЛР6: один pipeline — сборка → инфра (Terraform + Ansible) → при необходимости отдельный деплой-job.
// Новая логика: job «lab5-create-infra» сам кладёт JAR на ВМ при DEPLOY_QUIZBOT=true и JOB_BUILD_JAR = тому же job, что и сборка.
// Третий этап (JOB_DEPLOY) нужен только если инфра без деплоя; иначе оставь JOB_DEPLOY пустым в параметрах job.

pipeline {
    agent none

    options {
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    parameters {
        string(name: 'JOB_BUILD', defaultValue: 'java-build', description: 'Сборка JAR (артефакты build/libs/quizbot-app-*.jar)')
        string(name: 'JOB_INFRA', defaultValue: 'lab5-create-infra', description: 'Terraform + Ansible (+ деплой QuizBot при DEPLOY_QUIZBOT)')
        string(
            name: 'JOB_DEPLOY',
            defaultValue: '',
            description: 'Опционально: отдельный job деплоя. Пусто — деплой только внутри JOB_INFRA (рекомендуется).'
        )

        string(name: 'SERVER_NAME', defaultValue: 'emeshkin-bot-vm', description: 'Имя ВМ: reuse по OpenStack или новая')
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'OpenStack image (только для новой ВМ)')
        string(name: 'FLAVOR_NAME', defaultValue: '', description: 'OpenStack flavor (только для новой ВМ)')
        string(name: 'NETWORK_ID', defaultValue: '', description: 'OpenStack network UUID (только для новой ВМ)')
        string(name: 'KEY_PAIR', defaultValue: '', description: 'OpenStack keypair (только для новой ВМ)')
        string(name: 'SECURITY_GROUP', defaultValue: 'default', description: 'Security group')
        string(name: 'VOLUME_SIZE_GB', defaultValue: '10', description: 'Размер тома PostgreSQL, ГБ')
        booleanParam(
            name: 'DEPLOY_QUIZBOT',
            defaultValue: true,
            description: 'Передать в JOB_INFRA: после Ansible поставить PostgreSQL + JAR + systemd на ВМ'
        )
    }

    stages {
        stage('Build') {
            steps {
                script {
                    def buildResult = build job: params.JOB_BUILD, wait: true, propagate: true
                    echo "✅ Build: ${buildResult.fullDisplayName}"
                }
            }
        }

        stage('Create Infrastructure') {
            steps {
                script {
                    def jobBuild = (params.JOB_BUILD ?: 'java-build').toString().trim()
                    def infraResult = build(
                        job: params.JOB_INFRA,
                        wait: true,
                        propagate: true,
                        parameters: [
                            string(name: 'SERVER_NAME', value: (params.SERVER_NAME ?: '').toString()),
                            string(name: 'IMAGE_NAME', value: (params.IMAGE_NAME ?: '').toString()),
                            string(name: 'FLAVOR_NAME', value: (params.FLAVOR_NAME ?: '').toString()),
                            string(name: 'NETWORK_ID', value: (params.NETWORK_ID ?: '').toString()),
                            string(name: 'KEY_PAIR', value: (params.KEY_PAIR ?: '').toString()),
                            string(name: 'SECURITY_GROUP', value: (params.SECURITY_GROUP ?: 'default').toString()),
                            string(name: 'VOLUME_SIZE_GB', value: (params.VOLUME_SIZE_GB ?: '10').toString()),
                            booleanParam(name: 'DEPLOY_QUIZBOT', value: params.DEPLOY_QUIZBOT == true),
                            string(name: 'JOB_BUILD_JAR', value: jobBuild),
                        ]
                    )
                    echo "✅ Infrastructure: ${infraResult.fullDisplayName}"
                }
            }
        }

        stage('Deploy (optional)') {
            when {
                expression {
                    def n = (params.JOB_DEPLOY ?: '').toString().trim()
                    return n.length() > 0
                }
            }
            steps {
                script {
                    def deployResult = build job: params.JOB_DEPLOY.trim(), wait: true, propagate: true
                    echo "✅ Standalone deploy: ${deployResult.fullDisplayName}"
                }
            }
        }
    }
}
