// ЛР7: без создания облачной инфры — только образ + деплой в общий Kubernetes.
pipeline {
    agent none

    options {
        timeout(time: 120, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    parameters {
        string(name: 'JOB_BUILD_K8S', defaultValue: 'java-build-k8s', description: 'Job: docker build + push, артефакты docker_image.txt / docker_tag.txt')
        string(name: 'JOB_DEPLOY_K8S', defaultValue: 'lab7-k8s-deploy', description: 'Pipeline: kubectl apply + rollout (k8s/deploy.groovy)')
    }

    stages {
        stage('Build image') {
            steps {
                script {
                    def r = build job: params.JOB_BUILD_K8S, wait: true, propagate: true
                    echo "Image build completed: ${r.fullDisplayName}"
                }
            }
        }

        stage('Deploy to Kubernetes') {
            steps {
                script {
                    def r = build job: params.JOB_DEPLOY_K8S, wait: true, propagate: true
                    echo "K8s deploy completed: ${r.fullDisplayName}"
                }
            }
        }
    }
}
