// deploy/Jenkinsfile
// Ответственность: деплой приложения в Kubernetes-кластер (Minikube)
// Зависимости: 
//   - успешный build (артефакты: docker_image.txt, docker_tag.txt)
//   - настроенный k8s-kubeconfig credential в Jenkins
//   - файл .env в credential env-file-quizbot

pipeline {
    agent {
        label 'shihalev'  // Агент с kubectl, minikube, docker
    }

    options {
        timeout(time: 10, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        // Kubernetes
        K8S_NAMESPACE = 'quizbot'
        K8S_MANIFESTS_DIR = 'k8s'
        DEPLOY_TIMEOUT = '180s'
        
        // Пути к манифестам
        DEPLOYMENT_FILE = "${env.K8S_MANIFESTS_DIR}/deployment.yaml"
        SERVICE_FILE = "${env.K8S_MANIFESTS_DIR}/service.yaml"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "✅ Code checked out: ${env.GIT_COMMIT?.take(7)}"
            }
        }

        stage('Retrieve Build Artifacts') {
            steps {
                script {
                    // 👇 Копируем артефакты из последнего успешного build
                    copyArtifacts(
                        projectName: 'build',
                        selector: lastSuccessful(),
                        target: 'build-artifacts',
                        filter: 'docker_tag.txt,docker_image.txt',
                        fingerprintArtifacts: true
                    )
                    
                    // Загружаем переменные из файлов
                    env.DOCKER_TAG = readFile('build-artifacts/docker_tag.txt').trim()
                    env.DOCKER_IMAGE = readFile('build-artifacts/docker_image.txt').trim()
                    
                    echo "🐳 Will deploy: ${env.DOCKER_IMAGE}:${env.DOCKER_TAG}"
                    echo "🎯 Namespace: ${env.K8S_NAMESPACE}"
                }
            }
            post {
                failure {
                    echo "❌ Failed to retrieve build artifacts!"
                    echo "🔍 Check that 'build' job has successful runs with archived artifacts."
                    error("Artifact retrieval failed")
                }
            }
        }

        stage('🔐 Create Namespace') {
            steps {
                withKubeConfig([credentialsId: 'k8s-kubeconfig']) {
                    sh """kubectl create namespace ${env.K8S_NAMESPACE} \\
                        --dry-run=client -o yaml | kubectl apply -f -"""
                }
            }
        }

        stage('Create/Update Kubernetes Secrets') {
            steps {
                withCredentials([
                    file(credentialsId: 'env-file-quizbot', variable: 'ENV_FILE_PATH')
                ]) {
                    withKubeConfig([
                        credentialsId: 'k8s-kubeconfig',
                        serverUrl: '',
                        namespace: "${env.K8S_NAMESPACE}"
                    ]) {
                        script {
                            echo "🔐 Creating/updating secrets from .env file..."
                            
                            // Создаём или обновляем Secret из .env
                            // --dry-run=client + kubectl apply = идемпотентное обновление
                            sh """
                                kubectl create secret generic quizbot-secrets \\
                                    --from-env-file=${ENV_FILE_PATH} \\
                                    --namespace=${env.K8S_NAMESPACE} \\
                                    --dry-run=client -o yaml | kubectl apply -f -
                            """
                            
                            echo "✅ Secrets applied"
                        }
                    }
                }
            }
            post {
                failure {
                    echo "❌ Failed to create secrets! Check .env file format and credentials."
                }
            }
        }

        stage('Render Kubernetes Manifests') {
            steps {
                script {
                    echo "🔄 Rendering deployment.yaml with image info..."
                    
                    // Читаем шаблон и заменяем плейсхолдеры
                    def deploymentTemplate = readFile(file: "${env.DEPLOYMENT_FILE}")
                    def renderedDeployment = deploymentTemplate
                        .replace('{{ docker_image }}', env.DOCKER_IMAGE)
                        .replace('{{ docker_tag }}', env.DOCKER_TAG)
                    
                    // Пишем отрендеренный файл
                    writeFile(
                        file: 'deployment.rendered.yaml',
                        text: renderedDeployment
                    )
                    
                    echo "✅ Manifest rendered: deployment.rendered.yaml"
                }
            }
        }

        stage('Apply Kubernetes Resources') {
            steps {
                withKubeConfig([
                    credentialsId: 'k8s-kubeconfig',
                    serverUrl: '',
                    namespace: "${env.K8S_NAMESPACE}"
                ]) {
                    script {
                        echo "🚀 Applying Kubernetes manifests..."
                        
                        // Применяем в правильном порядке:
                        // 1. deployment.yaml (содержит Namespace, PVC, Deployments)
                        // 2. service.yaml (Services, зависят от Deployments)
                        sh """
                            kubectl apply -f deployment.rendered.yaml
                            kubectl apply -f ${env.SERVICE_FILE}
                        """
                        
                        // Ждём, пока деплоймент обновится
                        echo "⏳ Waiting for rollout to complete..."
                        sh "kubectl rollout status deployment/quizbot -n ${env.K8S_NAMESPACE} --timeout=${env.DEPLOY_TIMEOUT}"
                        
                        echo "✅ Rollout completed"
                    }
                }
            }
            post {
                failure {
                    echo "❌ Deployment failed! Debug info:"
                    withKubeConfig([credentialsId: 'k8s-kubeconfig']) {
                        sh "kubectl describe deployment quizbot -n ${env.K8S_NAMESPACE} || true"
                        sh "kubectl get pods -n ${env.K8S_NAMESPACE} -o wide || true"
                        sh "kubectl logs -n ${env.K8S_NAMESPACE} -l app=quizbot --tail=50 || true"
                    }
                }
            }
        }

        stage('Health Check') {
            steps {
                script {
                    echo "⏳ Waiting for application to start..."
                    sleep 15  // Даём время контейнерам инициализироваться
                    
                    // Получаем IP Minikube для проверки
                    def minikubeIP = sh(
                        script: 'minikube ip 2>/dev/null || echo "127.0.0.1"',
                        returnStdout: true
                    ).trim()
                    
                    echo "🔍 Checking health at http://${minikubeIP}:30080/healthcheck"
                    
                    retry(10) {
                        sleep 5
                        sh "curl -f --connect-timeout 10 --max-time 30 http://${minikubeIP}:30080/healthcheck"
                    }
                    
                    echo "✅ Health check passed"
                }
            }
            post {
                failure {
                    echo "❌ Health check failed! Application may not be responding."
                    echo "🔍 Debug commands:"
                    echo "   kubectl get pods -n ${env.K8S_NAMESPACE}"
                    echo "   kubectl logs -n ${env.K8S_NAMESPACE} -l app=quizbot"
                    echo "   kubectl describe service quizbot-service -n ${env.K8S_NAMESPACE}"
                }
            }
        }
    }

    post {
        always {
            // Очищаем временные файлы
            sh "rm -f deployment.rendered.yaml"
            
            // Логируем завершение
            echo "📦 Deploy #${env.BUILD_NUMBER} finished."
        }
        
        success {
            script {
                def minikubeIP = sh(
                    script: 'minikube ip 2>/dev/null || echo "127.0.0.1"',
                    returnStdout: true
                ).trim()
                
                echo "🎉 Deploy completed successfully!"
                echo "🌐 Application: http://${minikubeIP}:30080"
                echo "🔗 Health: http://${minikubeIP}:30080/healthcheck"
                echo "📊 Pods: kubectl get pods -n ${env.K8S_NAMESPACE}"
                echo "🗄️  DB: kubectl exec -n ${env.K8S_NAMESPACE} deploy/postgres -- psql -U \$POSTGRES_USER -d quizbot"
            }
        }
        
        failure {
            echo "💥 Deploy FAILED!"
            echo "🔍 Check console output and run:"
            echo "   kubectl get events -n ${env.K8S_NAMESPACE} --sort-by='.lastTimestamp'"
            echo "   kubectl describe pod -n ${env.K8S_NAMESPACE} -l app=quizbot"
        }
        
        unstable {
            echo "⚠️ Deploy completed with warnings. Review health check results."
        }
    }
}