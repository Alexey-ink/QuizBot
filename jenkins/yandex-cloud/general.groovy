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
    }

    parameters {
        choice(
            name: 'ACTION',
            choices: ['deploy', 'destroy', 'redeploy'],
            description: 'Выберите действие'
        )
        booleanParam(
            name: 'FORCE_RECREATE',
            defaultValue: false,
            description: 'Пересоздать инфраструктуру (terraform apply -refresh-only)'
        )
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
                    sh 'terraform validate'
                }
                dir("${env.ANSIBLE_DIR}") {
                    sh 'ansible-playbook --syntax-check playbook.yml'
                }
            }
        }

        stage('Terraform: Infrastructure') {
            when {
                anyOf {
                    expression { params.ACTION in ['deploy', 'redeploy'] }
                }
            }
            steps {
                withCredentials([
                    string(credentialsId: 'yc-token', variable: 'YC_TOKEN'),
                    string(credentialsId: 'ssh-public-key', variable: 'SSH_PUB_KEY') // опционально
                ]) {
                    dir("${env.TF_DIR}") {
                        // Инициализация с безопасным хранением токена
                        sh '''
                            export TF_VAR_yc_token="${YC_TOKEN}"
                            export TF_VAR_ssh_public_key="${SSH_PUB_KEY}"
                            terraform init -backend-config="bucket=${TF_BACKEND_BUCKET}" -reconfigure
                        '''
                        
                        // Plan
                        sh 'terraform plan -out=tfplan -var="postgres_password=${YC_TOKEN}"'
                        
                        // Apply (только если не только план)
                        script {
                            if (params.ACTION != 'plan-only') {
                                sh 'terraform apply -auto-approve tfplan'
                            }
                        }
                        
                        // Сохраняем выходы для следующих стадий
                        sh '''
                            terraform output -raw server_public_ip > ../server_ip.txt
                            terraform output -raw postgres_disk_id > ../disk_id.txt
                            terraform output -raw server_name > ../server_name.txt
                        '''
                    }
                }
            }
            post {
                always {
                    // Архивируем terraform plan для аудита
                    archiveArtifacts artifacts: "${env.TF_DIR}/tfplan", allowEmptyArchive: true
                }
            }
        }

        stage('Ansible: VM Preparation') {
            when {
                anyOf {
                    expression { params.ACTION in ['deploy', 'redeploy'] }
                }
            }
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
                            // Читаем IP из предыдущей стадии
                            def serverIP = readFile(file: "../server_ip.txt").trim()
                            def diskId = readFile(file: "../disk_id.txt").trim()
                            
                            // Генерируем inventory динамически
                            writeFile(
                                file: 'inventory.yml'
                            )
                            
                            // Запускаем playbook
                            sh '''
                                ansible-playbook -i inventory.yml playbook.yml \
                                    --extra-vars "server_ip=${serverIP}" \
                                    --extra-vars "postgres_disk_id=${diskId}" \
                                    -v
                            '''
                        }
                    }
                }
            }
        }

        stage('Docker: Build & Push (optional)') {
            when {
                allOf {
                    expression { params.ACTION in ['deploy', 'redeploy'] }
                    expression { env.DOCKER_REGISTRY_URL != null }
                }
            }
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'docker-registry-creds',
                        usernameVariable: 'DOCKER_USER',
                        passwordVariable: 'DOCKER_PASS'
                    )
                ]) {
                    sh '''
                        echo "${DOCKER_PASS}" | docker login ${DOCKER_REGISTRY_URL} -u "${DOCKER_USER}" --password-stdin
                        # Если нужно собрать образ:
                        # docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} .
                        # docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                    '''
                }
            }
        }

        stage('Docker Compose: Deploy Application') {
            when {
                anyOf {
                    expression { params.ACTION in ['deploy', 'redeploy'] }
                }
            }
            steps {
                withCredentials([
                    file(credentialsId: 'env-file-quizbot', variable: 'ENV_FILE_PATH'),
                    sshUserPrivateKey(
                        credentialsId: 'ssh-private-key',
                        keyFileVariable: 'SSH_KEY_PATH'
                    )
                ]) {
                    script {
                        def serverIP = readFile(file: "server_ip.txt").trim()
                        def postgresMount = "/data/postgres"
                        
                        // Генерируем docker-compose.yml из шаблона
                        def composeContent = readFile(file: "${env.ANSIBLE_DIR}/../templates/docker-compose.yaml")
                            .replaceAll('\\{\\{ postgres_image \\}\\}', env.POSTGRES_IMAGE)
                            .replaceAll('\\{\\{ docker_image \\}\\}', env.DOCKER_IMAGE)
                            .replaceAll('\\{\\{ docker_tag \\}\\}', env.DOCKER_TAG)
                            .replaceAll('\\{\\{ postgres_disk_mount \\}\\}', postgresMount)
                        
                        writeFile(file: 'docker-compose.yml', text: composeContent)
                        
                        // Копируем файлы на сервер и деплоим
                        sh """
                            # Копируем docker-compose.yml и .env
                            scp -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no \\
                                docker-compose.yml \\
                                ${ENV_FILE_PATH} \\
                                ubuntu@${serverIP}:${env.DEPLOY_DIR}/
                            
                            # Запускаем приложение
                            ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no ubuntu@${serverIP} \\
                                "cd ${env.DEPLOY_DIR} && \\
                                 docker compose pull && \\
                                 docker compose up -d --remove-orphans"
                        """
                    }
                }
            }
            post {
                success {
                    echo "✅ Приложение деплоено на ${readFile(file: 'server_ip.txt').trim()}:8080"
                }
                failure {
                    echo "❌ Ошибка деплоя! Проверьте логи и выполните откат при необходимости."
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    def serverIP = readFile(file: "server_ip.txt").trim()
                    // Простая проверка доступности
                    retry(3) {
                        sh "curl -sf --connect-timeout 10 http://${serverIP}:8080/healthcheck || exit 1"
                    }
                }
            }
        }

        stage('Terraform: Destroy (optional)') {
            when {
                expression { params.ACTION == 'destroy' }
            }
            steps {
                withCredentials([string(credentialsId: 'yc-token', variable: 'YC_TOKEN')]) {
                    dir("${env.TF_DIR}") {
                        sh '''
                            export TF_VAR_yc_token="${YC_TOKEN}"
                            terraform init -backend-config="bucket=${TF_BACKEND_BUCKET}"
                            terraform destroy -auto-approve -var="postgres_password=${YC_TOKEN}"
                        '''
                    }
                }
            }
        }
    }

    post {
        always {
            // Очистка чувствительных файлов
            sh 'rm -f server_ip.txt disk_id.txt server_name.txt docker-compose.yml .env'
            
            // Логи
            archiveArtifacts artifacts: '**/*.log', allowEmptyArchive: true, fingerprint: true
        }
    }
}