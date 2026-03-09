pipeline {
    agent any
    
    environment {
        OS_CREDENTIALS_ID = 'shihalev-rc'
        STACK_NAME = "shihalev-quizbot-stack"
        
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'alexeyshihalev/quizbot'
        
        // Имя credentials в Jenkins (SSH Username with private key)
        SSH_KEY_NAME = 'shihalev'

        INFRA_ARTIFACT_JOB = 'shihalev/create-infra-pipeline'
        BUILD_ARTIFACT_JOB = 'shihalev/build-pipeline'
    }
    
    parameters {
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'Docker image name (optional, если пусто - берётся из build job)')
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo '📥 Клонируем репозиторий...'
                checkout scm
            }
        }

        stage('Prepare OpenStack Env') {
            steps {
                echo "🔐 Loading OpenStack credentials..."
                loadSecretsIntoEnv("${OS_CREDENTIALS_ID}")
                echo "🔑 Testing OpenStack connection..."
                sh '''
                    set +x
                    openstack token issue -f yaml
                '''
                printSuccess("Auth successful")
            }
        }

        // ========================================================================
        // 📦 Получаем Docker Image из Build Job
        // ========================================================================
        stage('Get Docker Image from Build Job') {
            steps {
                script {
                    if (!params.IMAGE_NAME) {
                        echo "📦 IMAGE_NAME не указан, получаем из build job..."
                
                        try {
                            copyArtifacts projectName: BUILD_ARTIFACT_JOB,
                                        filter: 'docker-image.txt',
                                        target: '.',
                                        selector: lastSuccessful(),
                                        flatten: true
                            
                            env.DOCKER_IMAGE = readFile('docker-image.txt').trim()
                            
                            printDebug("🔍 Debug: env.DOCKER_IMAGE='${env.DOCKER_IMAGE}'")
                            
                            if (!env.DOCKER_IMAGE) {
                                printError("❌ Файл docker-image.txt пустой!")
                                error("❌ Файл docker-image.txt пустой!")
                            }
                            
                            printSuccess("Docker image из build: ${env.DOCKER_IMAGE}")
                            
                        } catch (Exception e) {
                            printError("❌ Ошибка: ${e.message}")
                            throw e
                        }
                    } else {
                        env.DOCKER_IMAGE = params.IMAGE_NAME
                        printSuccess("✅ Docker image из параметра: ${env.DOCKER_IMAGE}")
                    }
                }
            }
        }

        // ========================================================================
        // 🖥️ Получаем VM IP из Infra Job
        // ========================================================================
        stage('Get VM IP from Infra Job') {
            steps {
                script {
                    echo "🖥️ Получаем IP виртуалки из артефактов infra job..."
                    
                    copyArtifacts projectName: INFRA_ARTIFACT_JOB,
                                filter: 'stack_outputs.json',
                                target: '.',
                                selector: lastSuccessful(),
                                flatten: true
                    
                    def jsonContent = readFile('stack_outputs.json')
                    def outputs = readJSON text: jsonContent
                    def vmIpOutput = readJSON text: outputs['server_private_ip']
                    
                    if (vmIpOutput) {
                        env.VM_IP = vmIpOutput['output_value'].trim()
                        printSuccess("✅ VM IP: ${env.VM_IP}")
                    } else {
                        echo "⚠️ Доступные outputs:"
                        outputs.each { out ->
                            echo "  - ${out}"
                        }
                        error("❌ Не найдено 'server_private_ip'")
                    }
                }
            }
        }

        stage('Test SSH Connection') {
            steps {
                sshagent(["${SSH_KEY_NAME}"]) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no ubuntu@${VM_IP} "echo ✅ SSH OK"
                    '''
                }
            }
        }
        
        // ========================================================================
        // 🐳 Pull Docker Image на VM (с SSH ключом из Jenkins)
        // ========================================================================
        stage('Pull Docker Image on VM') {
            steps {
                sshagent(["${SSH_KEY_NAME}"]) {
                    script {
                        def VM_USER = 'ubuntu'
                        def APP_DIR = '/opt/quizbot'
                        
                        echo "🐳 Deploying to ${VM_USER}@${env.VM_IP}..."
                        
                        printStep("Copying docker-compose.yaml...")
                        sh """
                            scp -o StrictHostKeyChecking=no \\
                                -o UserKnownHostsFile=/dev/null \\
                                docker-compose.yaml \\
                                ${VM_USER}@${env.VM_IP}:~/docker-compose.yaml.tmp
                        """

                        printStep("Copying .env from credentials...")

                        withCredentials([file(credentialsId: 'quizbot-env-file', variable: 'ENV_FILE')]) {
                            sh """
                                scp -o StrictHostKeyChecking=no \\
                                    -o UserKnownHostsFile=/dev/null \\
                                    "\${ENV_FILE}" \\
                                    ${VM_USER}@${env.VM_IP}:/tmp/.env.tmp
                            """
                        }
                        
                        printStep("Pulling image and restarting containers...")
                        
                        sh """
                            ssh -o StrictHostKeyChecking=no \\
                                -o UserKnownHostsFile=/dev/null \\
                                ${VM_USER}@${env.VM_IP} << 'REMOTEOF'
                                
                                set -e
                                APP_DIR="${APP_DIR}"
                                
                                echo "📁 Moving files to app directory..."
                                
                                sudo mv ~/docker-compose.yaml.tmp \${APP_DIR}/docker-compose.yaml
                                sudo chown ${VM_USER}:${VM_USER} \${APP_DIR}/docker-compose.yaml

                                sudo mv /tmp/.env.tmp \${APP_DIR}/.env
                                sudo chown ${VM_USER}:${VM_USER} \${APP_DIR}/.env
                                sudo chmod 600 \${APP_DIR}/.env
                                
                                cd \${APP_DIR}
                                
                                echo "📥 Pulling image: ${env.DOCKER_IMAGE}"
                                docker pull ${env.DOCKER_IMAGE}
                                
                                echo "🔄 Updating image tag in docker-compose.yaml"
                                sudo sed -i "s|<image>|${env.DOCKER_IMAGE}|g" docker-compose.yaml
                                
                                echo "🚀 Restarting containers"
                                docker compose down
                                docker compose up -d
                                
                                echo "🧹 Cleaning up old images"
                                docker image prune -f
                                
                                echo "✅ Deployment complete"
    REMOTEOF
    """
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo "📊 Deployment completed"
            cleanWs()
        }
        failure {
            printError("Deployment failed! Check logs.")
        }
        success {
            printSuccess("Deployment successful! Image: ${env.DOCKER_IMAGE}, VM: ${env.VM_IP}")
        }
    }
}

// ============================================================================
// HELPER FUNCTIONS
// ============================================================================

def printInfo(String message)    { echo "ℹ️  ${message}" }
def printSuccess(String message) { echo "✅ ${message}" }
def printWarning(String message) { echo "⚠️  ${message}" }
def printError(String message)   { echo "❌ ${message}" }
def printDebug(String message)   { echo "🔍 ${message}" }
def printStep(String message)    { echo "📍 ${message}" }

// Загрузка openrc из FILE-credential в env.* переменные
def loadSecretsIntoEnv(String credentialId) {
    withCredentials([file(credentialsId: credentialId, variable: 'OPENRC_FILE')]) {
        def content = readFile file: OPENRC_FILE, encoding: 'UTF-8'
        
        content.split('\n').each { rawLine ->
            try {
                def line = rawLine.trim()
                if (!line || line.startsWith('#')) return
                
                // ✅ Удаляем префикс "export " если есть
                if (line.startsWith('export ')) {
                    line = line.substring(7).trim()
                }
                
                def parts = line.split('=', 2)
                if (parts.length != 2) return
                
                def key = parts[0].trim()
                def value = parts[1].trim()
                
                // Удаляем кавычки
                while (value.length() >= 2 && 
                      ((value.startsWith('"') && value.endsWith('"')) || 
                       (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1)
                }
                value = value.trim()
                
                // Записываем в env.*
                env."${key}" = value
                
                if (key == 'OS_PASSWORD') {
                    echo "✅ Loaded: ${key}=***"
                } else {
                    echo "✅ Loaded: ${key}=${value}"
                }
                
            } catch (Exception e) {
                echo "❌ Error loading ${key ?: 'unknown'}: ${e.message}"
            }
        }
    }
}