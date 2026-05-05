pipeline {
    agent { label 'emeshkin' }

    options {
        timeout(time: 20, unit: 'MINUTES')
        disableConcurrentBuilds()
        timestamps()
    }

    environment {
        // Совпадает с установкой JDK на агенте (при необходимости поменяй путь в Jenkins Global Tooling / здесь)
        JAVA_23_HOME = '/opt/jdk/jdk-23.0.2'
        JAVA_HOME = "${JAVA_23_HOME}"
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build (Gradle shadowJar)') {
            steps {
                script {
                    if (!fileExists('gradlew')) {
                        error '❌ Не найден gradlew в корне репозитория'
                    }
                }
                sh '''
                    set -euo pipefail
                    chmod +x ./gradlew
                    ./gradlew --version
                    ./gradlew clean shadowJar -x test --no-daemon
                '''
            }
        }

        stage('Archive artifacts') {
            steps {
                archiveArtifacts artifacts: 'build/libs/*.jar', allowEmptyArchive: false
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}
