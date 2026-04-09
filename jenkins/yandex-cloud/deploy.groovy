// Ответственность: деплой приложения на подготовленную инфраструктуру
// Автоматически берёт последние УСПЕШНЫЕ артефакты из create-infra и build

pipeline {
    agent {
        label 'shihalev'
    }

    options {
        timeout(time: 10, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        // Пути
        ANSIBLE_DIR = "jenkins/ansible"
        DEPLOY_DIR = "/opt/quizbot"
        TEMPLATE_DIR = "jenkins/templates"
        
        // Docker
        POSTGRES_IMAGE = "postgres:17"
        DOCKER_REGISTRY_URL = 'docker.io'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "✅ Code checked out: ${env.GIT_COMMIT?.take(7)}"
            }
        }

        stage('📥 Retrieve Latest Artifacts') {
            steps {
                script {
                    // Копируем последние успешные артефакты из create-infra
                    copyArtifacts(
                        projectName: 'create-infra',
                        selector: lastSuccessful(),
                        target: 'infra-artifacts',
                        filter: 'server_ip.txt,disk_id.txt,server_name.txt'
                    )
                    
                    // Копируем последние успешные артефакты из build
                    copyArtifacts(
                        projectName: 'build',
                        selector: lastSuccessful(),
                        target: 'build-artifacts',
                        filter: 'docker_tag.txt,docker_image.txt'
                    )

                    // Загружаем переменные из файлов
                    env.SERVER_IP = readFile('infra-artifacts/server_ip.txt').trim()
                    env.DISK_ID = readFile('infra-artifacts/disk_id.txt').trim()
                    env.SERVER_NAME = readFile('infra-artifacts/server_name.txt').trim()
                    env.DOCKER_TAG = readFile('build-artifacts/docker_tag.txt').trim()
                    env.DOCKER_IMAGE = readFile('build-artifacts/docker_image.txt').trim()

                    echo "🎯 Target: ${env.SERVER_NAME} (${env.SERVER_IP})"
                    echo "🐳 Image: ${env.DOCKER_IMAGE}:${env.DOCKER_TAG}"
                }
            }
        }

        stage('Prepare Deployment Files') {
            steps {
                script {
                    def postgresMount = "/data/postgres"
                    
                    // Генерируем docker-compose.yml из шаблона
                    def templatePath = "${env.TEMPLATE_DIR}/docker-compose.yaml"
                    def composeContent = readFile(file: templatePath)
                        .replaceAll('\\{\\{ postgres_image \\}\\}', env.POSTGRES_IMAGE)
                        .replaceAll('\\{\\{ docker_image \\}\\}', env.DOCKER_IMAGE)
                        .replaceAll('\\{\\{ docker_tag \\}\\}', env.DOCKER_TAG)
                        .replaceAll('\\{\\{ postgres_disk_mount \\}\\}', postgresMount)
                    
                    writeFile(file: 'docker-compose.yml', text: composeContent)
                    
                    echo "✅ docker-compose.yml generated"
                }
            }
        }

        stage('Copy Files to Server') {
            steps {
                withCredentials([
                    file(credentialsId: 'env-file-quizbot', variable: 'ENV_FILE_PATH'),
                    sshUserPrivateKey(
                        credentialsId: 'ssh-private-key',
                        keyFileVariable: 'SSH_KEY_PATH'
                    )
                ]) {
                    script {
                        sh """
                            scp -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no -o ConnectTimeout=10 \\
                                docker-compose.yml \\
                                ubuntu@${env.SERVER_IP}:${env.DEPLOY_DIR}/
                        """
                        
                        sh """
                            scp -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no -o ConnectTimeout=10 \\
                                ${ENV_FILE_PATH} \\
                                ubuntu@${env.SERVER_IP}:${env.DEPLOY_DIR}/.env
                        """
                        
                        sh """
                            ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no -o ConnectTimeout=10 \\
                                ubuntu@${env.SERVER_IP} \\
                                'chmod 640 ${env.DEPLOY_DIR}/.env ${env.DEPLOY_DIR}/docker-compose.yml'
                        """
                    }
                }
            }
        }

        stage('Deploy Application') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ssh-private-key',
                        keyFileVariable: 'SSH_KEY_PATH'
                    )
                ]) {
                    script {
                        echo "🔄 Pulling latest images and starting containers..."
                        
                        sh """
                            ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no -o ConnectTimeout=10 \\
                                ubuntu@${env.SERVER_IP} \\
                                "cd ${env.DEPLOY_DIR} && \\
                                docker-compose pull && \\
                                docker-compose up -d --remove-orphans"
                        """
                        
                        echo "✅ Containers started"
                    }
                }
            }
            post {
                failure {
                    echo "❌ Deployment failed! Check server logs:"
                    echo "   ssh ubuntu@${env.SERVER_IP} 'docker-compose -C ${env.DEPLOY_DIR} logs'"
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    echo "⏳ Waiting for application to start..."
                    sleep 15  // Даём время контейнерам подняться
                    
                    retry(10) {
                        sleep 10
                        echo "🔍 Checking health endpoint..."
                        sh "curl -f --connect-timeout 10 http://${env.SERVER_IP}:8080/healthcheck"
                    }
                    echo "✅ Health check passed"
                }
            }
            post {
                failure {
                    echo "❌ Health check failed! Application may not be responding."
                }
            }
        }
    }

    post {
        always {
            // Очищаем временные файлы
            sh "rm -f docker-compose.yml"
            echo "📦 Deploy #${env.BUILD_NUMBER} finished."
        }
        success {
            echo "🎉 deploy completed successfully!"
            echo "🌐 Application: http://${env.SERVER_IP}:8080"
            echo "🔗 Health: http://${env.SERVER_IP}:8080/healthcheck"
        }
        failure {
            echo "💥 deploy FAILED! Check console output for details."
        }
    }
}