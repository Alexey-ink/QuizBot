pipeline {
    agent { label 'shihalev' }

    options {
        timeout(time: 20, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Run Build') {
            steps {
                script {
                    // Запускаем джоб 'build' и ждём завершения
                    def buildResult = build(
                        job: 'build',
                        waitForCompletion: true,
                        propagate: true,  // если build упадёт — этот пайплайн тоже упадёт
                        parameters: [
                            string(name: 'GIT_COMMIT', value: "${env.GIT_COMMIT}")
                        ]
                    )
                    // Сохраняем ссылку на билд для копирования артефактов
                    env.BUILD_JOB_URL = buildResult.absoluteUrl
                    echo "✅ Build completed: ${buildResult.fullDisplayName}"
                }
            }
        }

        stage('Run Create-Infra') {
            steps {
                script {
                    def infraResult = build(
                        job: 'create-infra',
                        waitForCompletion: true,
                        propagate: true
                    )
                    env.INFRA_JOB_URL = infraResult.absoluteUrl
                    echo "✅ Infrastructure created: ${infraResult.fullDisplayName}"
                }
            }
        }

        stage('Run Deploy') {
            steps {
                script {
                    // Deploy сам заберёт артефакты из build и create-infra через copyArtifacts
                    def deployResult = build(
                        job: 'deploy',
                        waitForCompletion: true,
                        propagate: true
                    )
                    echo "✅ Deploy completed: ${deployResult.fullDisplayName}"
                }
            }
        }
    }

    post {
        success {
            echo "🎉 All stages completed successfully!"
        }
        failure {
            echo "💥 Failed at stage: ${currentBuild.currentStage?.name}"
            echo "🔍 Check: ${env.BUILD_URL}"
        }
    }
}