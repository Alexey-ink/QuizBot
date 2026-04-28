pipeline {
    // AGENT: нода, где выполняется pipeline
    agent {
        label 'openstack'
    }

    options {
        timeout(time: 10, unit: 'MINUTES')
        
        // запретить одновременный запуск нескольких сборок одного проекта
        disableConcurrentBuilds()
        timestamps()  // Показывать время в логах каждой команды
    }

    // ENVIRONMENT: Переменные окружения для всех стадий
    environment {
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_REPO = 'alexeyshihalev/quizbot'
        // Тег образа = номер сборки в Jenkins (уникальный для каждого билда)
        IMAGE_TAG = "${env.BUILD_NUMBER}"
        
        // Переменная для файла с секретами (настраивается в Jenkins Credentials)
        ENV_FILE = '.env' 
    }

    stages {
        
        stage('Checkout') {
            steps {
                echo 'Клонируем репозиторий...'
                
                // checkout scm - стандартная команда Jenkins для получения кода
                // scm автоматически использует настройки репозитория из job
                checkout scm
                
                echo '✅ Код получен из репозитория'
            }
        }

        stage('Build & Test') {
            // 🔨 Компиляция проекта и запуск тестов через Gradle
            steps {
                echo '🔨 Собираем проект через Gradle...'
                
                // script {} позволяет использовать Groovy-код внутри stages
                script {
                    // Проверяем наличие Gradle Wrapper (рекомендуемый подход)
                    // Wrapper гарантирует одинаковую версию Gradle у всех разработчиков
                    if (fileExists('gradlew')) {
                        echo '✅ Найден Gradle Wrapper'
                        
                        // Команды выполняются в bash-оболочке агента
                        sh '''
                            chmod +x ./gradlew
                            
                            # clean - очистка предыдущих сборок
                            # shadowJar - создание fat-jar (все зависимости в одном файле)
                            # -x test - пропустить тесты
                            # --no-daemon - не запускать фоновый процесс (важно для CI)
                            ./gradlew clean shadowJar -x test --no-daemon
                        '''
                    } else {
                        // Если wrapper нет - сборка прерывается
                        error '❌ Gradle Wrapper не найден! Добавьте gradlew в репозиторий.'
                    }
                }
                
                // Сохраняем скомпилированный JAR как артефакт
                // Можно скачать из UI Jenkins после сборки
                archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: true
            }
        }

        stage('Docker Build') {
            steps {
                echo '🐳 Сборка Docker-образа...'
                
                script {
                    // Локальный тег для сборки (не пушится в registry)
                    env.DOCKER_IMAGE_LOCAL = "${DOCKER_REGISTRY}/${DOCKER_REPO}:local"
                    
                    // ⚠️ ВНИМАНИЕ: .env создаётся только для сборки, НЕ добавляется в образ!
                    sh '''
                        # Копируем .env из Jenkins credentials (ENV_FILE)
                        cp "$ENV_FILE" .env 2>/dev/null || true
                        
                        # Сборка образа с локальным тегом
                        docker build \
                            -t ${DOCKER_IMAGE_LOCAL} \
                            -f Dockerfile \
                            .
                        
                        # Удаляем .env после сборки (безопасность!)
                        rm -f .env
                    '''
                }
            }
        }

        stage('Docker Push') {
            
            // when {
            //    branch 'main'
            // }
            
            steps {
                echo '🚀 Публикация образа в Docker Hub...'
                
                script {
                    // BUILD_NUMBER - встроенная переменная Jenkins (1, 2, 3...)
                    env.DOCKER_IMAGE = "${DOCKER_REGISTRY}/${DOCKER_REPO}:${IMAGE_TAG}"
                    env.DOCKER_IMAGE_LATEST = "${DOCKER_REGISTRY}/${DOCKER_REPO}:latest"
                }
                
                // withCredentials - безопасное использование секретов
                // credentialsId настраивается в: Jenkins → Credentials → System → Global
                withCredentials([usernamePassword(
                    credentialsId: 'shihalev-docker-registry-creds',  // ID из Jenkins Credentials
                    usernameVariable: 'DOCKER_USER',         
                    passwordVariable: 'DOCKER_PASS'        
                )]) {
                    // Все команды внутри выполняются с доступом к DOCKER_USER и DOCKER_PASS
                    sh """
                        # Авторизация в Docker Hub
                        # --password-stdin безопаснее, чем передача пароля в аргументах
                        echo "\${DOCKER_PASS}" | docker login ${DOCKER_REGISTRY} -u "\${DOCKER_USER}" --password-stdin
                        
                        # Создаём теги для образа
                        # :${IMAGE_TAG} - версия с номером сборки (например, :42)
                        # :latest - всегда указывает на последнюю стабильную версию
                        docker tag ${DOCKER_IMAGE_LOCAL} ${DOCKER_IMAGE}
                        docker tag ${DOCKER_IMAGE_LOCAL} ${DOCKER_IMAGE_LATEST}
                        
                        # Отправляем образы в Docker Hub
                        docker push ${DOCKER_IMAGE}
                        docker push ${DOCKER_IMAGE_LATEST}
                        
                        # Выход из реестра (хорошая практика безопасности)
                        docker logout ${DOCKER_REGISTRY}
                        
                        # Сохраняем имя образа в файл для передачи в deploy-стадию
                        # Это нужно, если деплой выполняется в отдельной job
                        echo "${DOCKER_IMAGE}" > docker-image.txt
                    """
                }
                
                // Архивируем файл с именем образа
                // Можно использовать в downstream jobs через copyArtifacts
                archiveArtifacts artifacts: 'docker-image.txt', allowEmptyArchive: true
            }
        }
    }

    // POST: Действия после завершения pipeline (всегда выполняются)
    post {
        always {
            echo '🧹 Очистка после сборки...'
            
            // Удаляем локальный Docker-образ
            sh "docker rmi ${DOCKER_REGISTRY}/${DOCKER_REPO}:local 2>/dev/null || true"
            
            sh 'rm -f .env docker-image.txt'
            
            // cleanWs() - стандартная команда Jenkins для очистки workspace
            // Удаляет все файлы, скачанные на этапе checkout
            cleanWs()
        }
        
        failure {
            echo '❌ Сборка провалилась! Проверьте логи выше.'
        }
        
        success {
            echo "🎉 Сборка успешна!"
            echo "📦 Образ: ${DOCKER_REGISTRY}/${DOCKER_REPO}:${IMAGE_TAG}"
            echo "🔗 Docker Hub: https://hub.docker.com/r/${DOCKER_REPO}/tags"
            
        }
        
        aborted {
            echo '🛑 Сборка была отменена пользователем'
        }
    }
}