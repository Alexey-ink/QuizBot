pipeline {
    agent {
        label 'shihalev'
    }

    environment {
        TF_IN_AUTOMATION = "1"
        TF_INPUT = "0"
        ANSIBLE_HOST_KEY_CHECKING = "False"
        
        // Пути к артефактам
        TF_DIR = "infrastructure/terraform"
        ANSIBLE_DIR = "jenkins/ansible"
        DEPLOY_DIR = "/opt/quizbot"
        ENV_FILE_NAME = ".env"
        
        POSTGRES_IMAGE = "postgres:17"
        DOCKER_IMAGE = "alexeyshihalev/quizbot"
        DOCKER_TAG = "${env.BUILD_NUMBER}"
        DOCKER_REGISTRY_URL = 'docker.io'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                // Показываем структуру для отладки
                sh 'find . -maxdepth 3 -type f -name "*.tf" -o -name "*.yml" -o -name "general.groovy" | head -20'
            }
        }

        stage('Validate & Format') {
            steps {
                dir("${env.TF_DIR}") {
                    sh 'terraform fmt -check -recursive || terraform fmt -recursive'
                    
                    // Инициализируем провайдеры (без бэкенда, чтобы не требовать доступ к хранилищу)
                    sh 'terraform init -backend=false'
                    
                    sh 'terraform validate'
                }
                dir("${env.ANSIBLE_DIR}") {
                    sh 'ansible-playbook --syntax-check playbook.yml'
                }
            }
        }

        stage('Terraform: Infrastructure') {
            steps {
                withCredentials([
                    string(credentialsId: 'yc-token', variable: 'YC_TOKEN'),
                    string(credentialsId: 'ssh-public-key', variable: 'SSH_PUB_KEY')
                ]) {
                    dir("${env.TF_DIR}") {
                        // Init
                        sh "terraform init -reconfigure"
                        
                        // Plan — передаём все переменные явно
                        sh """
                            terraform plan -out=tfplan \\
                                -var='yc_token=${YC_TOKEN}' \\
                                -var='ssh_public_key=${SSH_PUB_KEY}' \\
                                -var='postgres_password=${YC_TOKEN}'
                        """
                        
                        // Apply
                        script {
                            sh "terraform apply -auto-approve tfplan"      
                        }

                        sh """
                            terraform output -raw server_public_ip > ${env.WORKSPACE}/server_ip.txt
                            terraform output -raw postgres_disk_id > ${env.WORKSPACE}/disk_id.txt
                            terraform output -raw server_name > ${env.WORKSPACE}/server_name.txt
                        """
                    }
                }
            }
        }

       stage('Ansible: VM Preparation') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ssh-private-key',
                        keyFileVariable: 'SSH_KEY_PATH',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    dir("${env.ANSIBLE_DIR}") {
                        script {
                            def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                            def diskId = readFile(file: "${env.WORKSPACE}/disk_id.txt").trim()
                            
                            echo "Waiting for SSH on ${serverIP}..."
                    
                            // Ждем, пока порт 22 не станет доступен (таймаут 120 секунд)
                            sh """
                                timeout 120 bash -c 'until nc -z ${serverIP} 22; do sleep 5; done'
                            """
                            
                            echo "✅ SSH is ready. Starting Ansible..."

                            if (!serverIP) {
                                error("server_ip is empty!")
                            }
                            echo "📡 Deploying to: ${serverIP}"
                            
                            sh """
                                chmod 600 ${SSH_KEY_PATH} && \\
                                ansible-playbook -i inventory.yml playbook.yml \\
                                    --extra-vars "server_ip=${serverIP}" \\
                                    --extra-vars "postgres_disk_id=${diskId}" \\
                                    --extra-vars "ansible_ssh_private_key_file=${SSH_KEY_PATH}" \\
                                    --extra-vars "ansible_user=${SSH_USER}" \\
                                    -v
                            """
                        }
                    }
                }
            }
        }

        stage('Docker: Build & Push (optional)') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'shihalev-docker-registry-creds',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh """
                        echo "${DOCKER_PASS}" | docker login ${DOCKER_REGISTRY_URL} -u "${DOCKER_USER}" --password-stdin
                        # Если нужно собрать образ:
                        docker build --platform linux/amd64 -t ${DOCKER_IMAGE}:${DOCKER_TAG} .
                        docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                    """
                }
            }
        }

        stage('Docker Compose: Deploy Application') {
            steps {
                withCredentials([
                    file(credentialsId: 'env-file-quizbot', variable: 'ENV_FILE_PATH'),
                    sshUserPrivateKey(
                        credentialsId: 'ssh-private-key',
                        keyFileVariable: 'SSH_KEY_PATH'
                    )
                ]) {
                    script {
                        def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                        def postgresMount = "/data/postgres"
                        
                        // Генерируем docker-compose.yml из шаблона
                        def composeContent = readFile(file: "${env.ANSIBLE_DIR}/../templates/docker-compose.yaml")
                            .replaceAll('\\{\\{ postgres_image \\}\\}', env.POSTGRES_IMAGE)
                            .replaceAll('\\{\\{ docker_image \\}\\}', env.DOCKER_IMAGE)
                            .replaceAll('\\{\\{ docker_tag \\}\\}', env.DOCKER_TAG)
                            .replaceAll('\\{\\{ postgres_disk_mount \\}\\}', postgresMount)
                        
                        writeFile(file: 'docker-compose.yml', text: composeContent)
                        
                        // ✅ Единый блок: фикс прав + копирование с правильным именем + запуск
                        sh """
                            # 1. Исправляем права на ВМ (если файл существует) — для ОБОИХ возможных имён
                            ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no ubuntu@${serverIP} \\
                                'for f in ${env.DEPLOY_DIR}/.env ${env.DEPLOY_DIR}/app.env; do \\
                                    test -f "\$f" && chmod u+w "\$f" || true; \\
                                done'
                            
                            # 2. Копируем docker-compose.yml
                            scp -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no \\
                                docker-compose.yml \\
                                ubuntu@${serverIP}:${env.DEPLOY_DIR}/
                            
                            # 3. Копируем .env с ЯВНЫМ указанием целевого имени (важно!)
                            scp -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no \\
                                ${ENV_FILE_PATH} \\
                                ubuntu@${serverIP}:${env.DEPLOY_DIR}/.env
                            
                            # 4. Фиксируем права после копирования
                            ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no ubuntu@${serverIP} \\
                                'chmod 664 ${env.DEPLOY_DIR}/.env ${env.DEPLOY_DIR}/docker-compose.yml'
                            
                            # 5. Запускаем приложение
                            ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no ubuntu@${serverIP} \\
                                "cd ${env.DEPLOY_DIR} && \\
                                docker-compose pull && \\
                                docker-compose up -d --remove-orphans"
                        """
                    }
                }
            }
            post {
                success {
                    echo "✅ Приложение деплоено на ${readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()}:8080"
                }
                failure {
                    echo "❌ Ошибка деплоя! Проверьте логи и выполните откат при необходимости."
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def ip = readFile('server_ip.txt').trim()
                    retry(10) {
                        sleep 10
                        sh "curl -f --connect-timeout 10 http://${ip}:8080/healthcheck"
                    }
                }
            }
        }
        
    post {
        always {
            sh """
                rm -f ${env.WORKSPACE}/server_ip.txt \\
                    ${env.WORKSPACE}/disk_id.txt \\
                    ${env.WORKSPACE}/server_name.txt \\
                    ${env.WORKSPACE}/docker-compose.yml \\
                    ${env.WORKSPACE}/.env
            """
        }
    }
}