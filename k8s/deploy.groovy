// Ответственность: деплой приложения в Kubernetes-кластер (Minikube)
// Зависимости: 
//   - успешный build (артефакты: docker_image.txt, docker_tag.txt)
//   - Secret file k8s-kubeconfig (kubeconfig) + env-file-quizbot (.env)
//   - kubectl на агенте; withCredentials + KUBECONFIG (без плагина Kubernetes CLI)

pipeline {
    agent {
        label 'emeshkin'
    }

    options {
        timeout(time: 10, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    environment {
        K8S_NAMESPACE = 'quizbot'
        K8S_MANIFESTS_DIR = 'k8s'
        DEPLOY_TIMEOUT = '180s'
        
        DEPLOYMENT_FILE = "${env.K8S_MANIFESTS_DIR}/deployment.yaml"
        SERVICE_FILE = "${env.K8S_MANIFESTS_DIR}/service.yaml"

        HEALTH_CHECK_PORT = '9090'
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
                    // Копируем артефакты из последнего успешного build
                    copyArtifacts(
                        projectName: 'java-build-k8s',
                        selector: lastSuccessful(),
                        target: 'build-artifacts',
                        filter: 'docker_tag.txt,docker_image.txt',
                        fingerprintArtifacts: true
                    )
                    
                    env.DOCKER_TAG = readFile('build-artifacts/docker_tag.txt').trim()
                    env.DOCKER_IMAGE = readFile('build-artifacts/docker_image.txt').trim()
                    
                    echo "🐳 Will deploy: ${env.DOCKER_IMAGE}:${env.DOCKER_TAG}"
                    echo "🎯 Namespace: ${env.K8S_NAMESPACE}"
                }
            }
            post {
                failure {
                    echo "❌ Failed to retrieve build artifacts!"
                    echo "🔍 Check that 'java-build-k8s' has successful runs with archived docker_image.txt and docker_tag.txt."
                    error("Artifact retrieval failed")
                }
            }
        }

        stage('Create Namespace') {
            steps {
                withCredentials([file(credentialsId: 'k8s-kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                    sh """
                        export KUBECONFIG="\$KUBECONFIG_FILE"
                        kubectl create namespace ${env.K8S_NAMESPACE} \\
                            --dry-run=client -o yaml | kubectl apply -f -
                    """
                }
            }
        }

        stage('Create/Update Kubernetes Secrets') {
            steps {
                withCredentials([
                    file(credentialsId: 'env-file-quizbot', variable: 'ENV_FILE_PATH'),
                    file(credentialsId: 'k8s-kubeconfig', variable: 'KUBECONFIG_FILE')
                ]) {
                    script {
                        echo "🔐 Creating/updating secrets from .env file..."
                        sh """
                            export KUBECONFIG="\$KUBECONFIG_FILE"
                            kubectl create secret generic quizbot-secrets \\
                                --from-env-file="\$ENV_FILE_PATH" \\
                                --namespace=${env.K8S_NAMESPACE} \\
                                --dry-run=client -o yaml | kubectl apply -f -
                        """
                        echo "✅ Secrets applied"
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
                withCredentials([file(credentialsId: 'k8s-kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                    script {
                        echo "🚀 Applying Kubernetes manifests..."
                        sh """
                            export KUBECONFIG="\$KUBECONFIG_FILE"
                            kubectl apply -f deployment.rendered.yaml
                            kubectl apply -f ${env.SERVICE_FILE}
                        """
                        echo "⏳ Waiting for rollout to complete..."
                        sh """
                            export KUBECONFIG="\$KUBECONFIG_FILE"
                            kubectl rollout status deployment/quizbot -n ${env.K8S_NAMESPACE} --timeout=${env.DEPLOY_TIMEOUT}
                        """
                        echo "✅ Rollout completed"
                    }
                }
            }
            post {
                failure {
                    echo "❌ Deployment failed! Debug info:"
                    withCredentials([file(credentialsId: 'k8s-kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                        sh """
                            export KUBECONFIG="\$KUBECONFIG_FILE"
                            kubectl describe deployment quizbot -n ${env.K8S_NAMESPACE} || true
                            kubectl get pods -n ${env.K8S_NAMESPACE} -o wide || true
                            kubectl logs -n ${env.K8S_NAMESPACE} -l app=quizbot --tail=50 || true
                        """
                    }
                }
            }
        }

        stage('Verify Deployment') {
            steps {
                withCredentials([file(credentialsId: 'k8s-kubeconfig', variable: 'KUBECONFIG_FILE')]) {
                    sh """
                        export KUBECONFIG="\$KUBECONFIG_FILE"
                        echo "📊 Pods:"
                        kubectl get pods -n ${env.K8S_NAMESPACE} -o wide

                        echo "🖥 Nodes:"
                        kubectl get nodes -o wide

                        echo "🌐 Services:"
                        kubectl get svc -n ${env.K8S_NAMESPACE}

                        echo "🔗 Starting port-forward..."
                        kubectl port-forward -n ${env.K8S_NAMESPACE} \\
                            svc/quizbot-service 9090:80 > pf.log 2>&1 &
                        PF_PID=\$!

                        echo "⏳ Waiting for service..."
                        i=1
                        while [ \$i -le 10 ]; do
                            if curl -s http://localhost:9090/healthcheck; then
                                echo "✅ Healthcheck OK"
                                break
                            fi
                            i=\$((i + 1))
                            sleep 2
                        done

                        echo "📄 Port-forward log:"
                        cat pf.log || true

                        echo "🛑 Stopping port-forward..."
                        kill \$PF_PID 2>/dev/null || true
                    """
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
            sh "rm -f deployment.rendered.yaml"
            echo "📦 Deploy #${env.BUILD_NUMBER} finished."
        }
        
        success {
            echo "🎉 Deploy completed successfully!"

            echo "📊 Pods:"
            echo "   kubectl get pods -n ${env.K8S_NAMESPACE}"

            echo "🌐 If using Minikube:"
            echo "   minikube service quizbot-service --url"

            echo "🔗 Or via NodePort:"
            echo "   http://<minikube-ip>:30080/healthcheck"
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