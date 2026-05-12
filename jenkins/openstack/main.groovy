pipeline {
    agent { label 'emeshkin' }

    options {
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'JOB_CREATE_INFRA', defaultValue: 'openstack/create-infra', description: 'Jenkins job: Heat stack apply + stack_outputs.json')
        string(name: 'JOB_BUILD', defaultValue: 'openstack/build', description: 'Jenkins job: Gradle build + jar artifact')
        string(name: 'JOB_DEPLOY', defaultValue: 'openstack/deploy', description: 'ЛР3: копия quizbot.jar + deploy/systemd/quizbot.service, enable/restart quizbot')
        string(name: 'STACK_NAME', defaultValue: 'emeshkin-lab3-stack', description: 'Heat stack name for Lab 3')
        choice(name: 'HEAT_ACTION', choices: ['update', 'create', 'recreate'], description: 'Lab 3 infra mode: update existing stack or create/recreate')
        string(name: 'SERVER_NAME', defaultValue: 'emeshkin-bot-vm', description: 'Имя ВМ: reuse если есть, иначе создаётся (нужны IMAGE/FLAVOR/NETWORK/KEY)')
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'Образ для новой ВМ (если ВМ ещё нет)')
        string(name: 'FLAVOR_NAME', defaultValue: '', description: 'Flavor для новой ВМ')
        string(name: 'NETWORK_ID', defaultValue: '', description: 'UUID сети для новой ВМ')
        string(name: 'KEY_PAIR', defaultValue: '', description: 'Keypair для новой ВМ')
        string(name: 'SECURITY_GROUP', defaultValue: 'default', description: 'Security group для новой ВМ')
    }

    stages {
        stage('Build') {
            steps {
                script {
                    def r = build job: params.JOB_BUILD, wait: true, propagate: true
                    echo "✅ Build OK: ${r.absoluteUrl}"
                }
            }
        }

        stage('Infra (Heat)') {
            steps {
                script {
                    def r = build(
                        job: params.JOB_CREATE_INFRA,
                        wait: true,
                        propagate: true,
                        parameters: [
                            string(name: 'STACK_NAME', value: params.STACK_NAME),
                            string(name: 'HEAT_ACTION', value: params.HEAT_ACTION),
                            string(name: 'SERVER_NAME', value: params.SERVER_NAME),
                            string(name: 'IMAGE_NAME', value: params.IMAGE_NAME),
                            string(name: 'FLAVOR_NAME', value: params.FLAVOR_NAME),
                            string(name: 'NETWORK_ID', value: params.NETWORK_ID),
                            string(name: 'KEY_PAIR', value: params.KEY_PAIR),
                            string(name: 'SECURITY_GROUP', value: params.SECURITY_GROUP)
                        ]
                    )
                    echo "✅ Infra OK: ${r.absoluteUrl}"
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    def r = build job: params.JOB_DEPLOY, wait: true, propagate: true
                    echo "✅ Deploy OK: ${r.absoluteUrl}"
                }
            }
        }
    }

    post {
        failure {
            echo "💥 Pipeline failed: ${env.BUILD_URL}"
        }
    }
}
