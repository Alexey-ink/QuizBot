pipeline {
    agent {
        label 'shihalev'
    }

    options {
        timeout(time: 10, unit: 'MINUTES')
        timestamps()               
        disableConcurrentBuilds()
    }

    environment {
        STACK_NAME = 'shihalev-quizbot-stack'
        HEAT_TEMPLATE = 'infrastructure/heat/stack.yaml'
        OS_CREDENTIALS_ID = 'shihalev-rc'
    }

    stages {
        
        stage('Checkout') {
            steps {
                echo '📥 Клонируем репозиторий...'
                checkout scm
                
                script {
                    if (!fileExists(env.HEAT_TEMPLATE)) {
                        error "❌ Heat-шаблон не найден: ${HEAT_TEMPLATE}"
                    }
                }
            }
        }

        stage('Load OpenStack Credentials') {
            steps {
                script {
                    echo "🔐 Loading OpenStack credentials..."
                    
                    loadSecretsIntoEnv(env.OS_CREDENTIALS_ID)
                    
                    echo "🔑 Testing OpenStack connection..."
                    sh '''
                        set +x
                        openstack token issue -f yaml | head -5
                    '''
                    echo "✅ Auth successful"
                }
            }
        }

        stage('Check & Cleanup Existing Infra') {
            steps {
                script {
                    echo "🧹 Проверка существующего стека '${STACK_NAME}'..."
                    
                    def stackStatus = sh(
                        script: "openstack stack show ${STACK_NAME} -f value -c stack_status 2>/dev/null || echo 'NOT_FOUND'",
                        returnStdout: true
                    ).trim()
                    
                    if (stackStatus == 'NOT_FOUND') {
                        echo "✅ Стек не найден, продолжаем..."
                    } else {
                        echo "⚠️ Стек существует (статус: ${stackStatus}), удаляем..."
                        
                        sh '''
                            openstack stack delete --yes ${STACK_NAME}
                        '''
                        
                        echo "⏳ Ожидание удаления (макс. 2 минуты)..."
                        sh '''
                            for i in {1..24}; do
                                STATUS=$(openstack stack show ${STACK_NAME} -f value -c stack_status 2>/dev/null || echo "DELETED")
                                if [ "$STATUS" == "DELETED" || "$STATUS" == "" ]; then
                                    echo "✅ Стек удалён"
                                    break
                                fi
                                echo "  Попытка $i/24, статус: $STATUS"
                                sleep 5
                            done
                        '''
                    }
                }
            }
        }

        stage('Deploy Infrastructure') {
            steps {
                script {
                    echo "🚀 Создание стека '${STACK_NAME}'..."
                    echo "   Шаблон: ${HEAT_TEMPLATE}"
                    
                    sh '''  
                        set +x                      
                        # ✅ Параметры подтянутся из дефолтов шаблона
                        openstack stack create \
                            -t ${HEAT_TEMPLATE} \
                            --wait \
                            ${STACK_NAME}
                    '''
                    
                    echo "✅ Стек создан успешно!"
                }
            }
        }

        stage('Collect Outputs') {
            steps {
                script {
                    echo "📥 Получаем выходные параметры стека..."
                    
                    env.SERVER_IP = sh(
                        script: "openstack stack output show -c output_value -f value ${STACK_NAME} server_private_ip",
                        returnStdout: true
                    ).trim()

                    if (!env.SERVER_IP) {
                        error("❌ Не удалось получить server_private_ip из стека ${STACK_NAME}")
                    }

                    echo "🌍 Инфраструктура готова: ${env.SERVER_IP}"
                    
                    sh """
                        openstack stack output show --all --format json ${STACK_NAME} > stack_outputs.json
                    """
            
                    echo "✅ Outputs сохранены в stack_outputs.json"
                    
                    archiveArtifacts artifacts: 'stack_outputs.json', allowEmptyArchive: false
                }
            }
        }
    }

    post {
        always {
            echo '📦 Завершение...'
            cleanWs()
        }
        failure {
            echo "❌ DEPLOYMENT FAILED"
            echo "🔍 Проверьте OpenStack dashboard"
            sh '''
                if openstack stack show ${STACK_NAME} &>/dev/null; then
                    echo "🗑️ Очищаем частично созданный стек..."
                    openstack stack delete --yes ${STACK_NAME} || true
                fi
            '''
        }
        success {
            echo "🎉 Инфраструктура создана успешно!"
            echo "🔐 SSH: ssh ubuntu@${env.SERVER_IP}"
        }
    }
}

// Загрузка openrc из FILE-credential в env.* переменные
def loadSecretsIntoEnv(String credentialId) {
    withCredentials([file(credentialsId: credentialId, variable: 'OPENRC_FILE')]) {
        def content = readFile file: OPENRC_FILE, encoding: 'UTF-8'
        
        content.split('\n').each { rawLine ->
            try {
                def line = rawLine.trim()
                if (!line || line.startsWith('#')) return
                
                // Разделяем по первому '='
                def parts = line.split('=', 2)
                if (parts.length != 2) return
                
                def key = parts[0].trim()
                def value = parts[1].trim()
                
                while (value.length() >= 2 && 
                      ((value.startsWith('"') && value.endsWith('"')) || 
                       (value.startsWith("'") && value.endsWith("'")))) {
                    value = value.substring(1, value.length() - 1)
                }
                value = value.trim()
                
                // записываем в env.* — доступно во всём пайплайне!
                env."${key}" = value
                echo "✅ Loaded: ${key}"
                
            } catch (Exception e) {
                echo "❌ Error loading ${key ?: 'unknown'}: ${e.message}"
            }
        }
    }
}