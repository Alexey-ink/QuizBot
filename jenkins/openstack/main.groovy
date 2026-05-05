pipeline {
    agent { label 'emeshkin' }

    options {
        timeout(time: 60, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'JOB_CREATE_INFRA', defaultValue: 'openstack/create-infra', description: 'Jenkins job: Heat stack apply + stack_outputs.json')
        string(name: 'JOB_BUILD', defaultValue: 'openstack/build', description: 'Jenkins job: Gradle build + jar artifact')
        string(name: 'JOB_DEPLOY', defaultValue: 'openstack/deploy', description: 'Jenkins job: copy jar + systemctl restart')
    }

    stages {
        stage('Build') {
            steps {
                script {
                    def r = build job: params.JOB_BUILD, wait: true, propagate: true
                    echo "✅ Build OK: ${r.absoluteUrl}"
                }
            }
        }

        stage('Infra (Heat)') {
            steps {
                script {
                    def r = build job: params.JOB_CREATE_INFRA, wait: true, propagate: true
                    echo "✅ Infra OK: ${r.absoluteUrl}"
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    def r = build job: params.JOB_DEPLOY, wait: true, propagate: true
                    echo "✅ Deploy OK: ${r.absoluteUrl}"
                }
            }
        }
    }

    post {
        failure {
            echo "💥 Pipeline failed: ${env.BUILD_URL}"
        }
    }
}
