// Сборка и публикация Docker-образа
// Тег образа: ${BUILD_NUMBER} (автоматически из Jenkins)
// Пуш: всегда в docker.io/alexeyshihalev/quizbot

pipeline {
    agent {
        label 'shihalev'
    }

    options {
        timeout(time: 15, unit: 'MINUTES')
        disableConcurrentBuilds() 
    }

    environment {
        DOCKER_IMAGE = "alexeyshihalev/quizbot"
        DOCKER_TAG = "${env.BUILD_NUMBER}"  
        DOCKER_REGISTRY_URL = 'docker.io'
        APP_DIR = "." 
    }

    stages {
        stage('📦 Checkout') {
            steps {
                checkout scm
                echo "✅ Code checked out: ${env.GIT_COMMIT?.take(5)}"
            }
        }

        stage('Build Docker Image') {
            steps {
                dir("${env.APP_DIR}") {
                    sh """
                        docker build --platform linux/amd64 \\
                            --build-arg BUILD_NUMBER=${env.BUILD_NUMBER} \\
                            --build-arg GIT_COMMIT=${env.GIT_COMMIT} \\
                            -t ${DOCKER_IMAGE}:${DOCKER_TAG} .
                    """
                }
            }
            post {
                success {
                    echo "✅ Image built: ${DOCKER_IMAGE}:${DOCKER_TAG}"
                }
                failure {
                    echo "❌ Docker build failed! Check Dockerfile and build context."
                    error("Build failed")
                }
            }
        }

        stage('Push to Docker Hub') {
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
                        docker push ${DOCKER_IMAGE}:${DOCKER_TAG}
                        docker logout ${DOCKER_REGISTRY_URL}
                    """
                }
            }
            post {
                success {
                    echo "✅ Image pushed: ${DOCKER_REGISTRY_URL}/${DOCKER_IMAGE}:${DOCKER_TAG}"
                }
                failure {
                    echo "❌ Failed to push image! Check credentials and Docker Hub status."
                    error("Push failed")
                }
            }
        }

        stage('Save Build Metadata') {
            steps {
                script {
                    writeFile(file: 'docker_tag.txt', text: "${env.DOCKER_TAG}")
                    writeFile(file: 'docker_image.txt', text: "${env.DOCKER_IMAGE}")
                    
                    def metadata = [
                        build_number: env.BUILD_NUMBER,
                        git_commit: env.GIT_COMMIT,
                        git_branch: env.GIT_BRANCH,
                        docker_image: env.DOCKER_IMAGE,
                        docker_tag: env.DOCKER_TAG,
                        timestamp: new Date().format('yyyy-MM-dd HH:mm:ss')
                    ]
                    writeFile(
                        file: 'build_metadata.json',
                        text: groovy.json.JsonOutput.prettyPrint(groovy.json.JsonOutput.toJson(metadata))
                    )
                }
            }
        }
    }

    post {
        always {
            // Архивируем артефакты для других пайплайнов
            archiveArtifacts(
                artifacts: 'docker_tag.txt,docker_image.txt,build_metadata.json',
                allowEmptyArchive: false,
                fingerprint: true
            )
            
            // Очищаем образ на агенте, чтобы не занимать место
            sh "docker rmi ${DOCKER_IMAGE}:${DOCKER_TAG} 2>/dev/null || true"
            
            echo "📦 Artifacts archived. Build #${env.BUILD_NUMBER} finished."
        }
        success {
            echo "🎉 build completed successfully!"
            echo "🐳 Image: ${DOCKER_IMAGE}:${DOCKER_TAG}"
            echo "🔗 Deploy with: INFRA_BUILD_ID=<id>, BUILD_BUILD_ID=${env.BUILD_NUMBER}"
        }
        failure {
            echo "💥 build FAILED!"
            echo "🔍 Check console output for details."
        }
    }
}