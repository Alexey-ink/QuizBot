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
        DOCKER_REGISTRY_URL = 'alexeyshihalev/quizbot'
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
                            if (params.ACTION != 'plan-only') {
                                sh "terraform apply -auto-approve tfplan"
                            }
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
                            def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                            def diskId = readFile(file: "${env.WORKSPACE}/disk_id.txt").trim()
                            
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
                    sh """
                        echo "${DOCKER_PASS}" | docker login ${DOCKER_REGISTRY_URL} -u "${DOCKER_USER}" --password-stdin
                        # Если нужно собрать образ:
                        # docker build -t ${DOCKER_IMAGE}:${DOCKER_TAG} .
                        # docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                    """
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
                        def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
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
                    def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
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
                            terraform init"
                            terraform destroy -auto-approve -var="postgres_password=${YC_TOKEN}"
                        '''
                    }
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