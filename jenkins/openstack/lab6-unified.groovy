pipeline {
    agent none

    options {
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
        skipDefaultCheckout(true)
    }

    parameters {
        string(name: 'JOB_BUILD', defaultValue: 'java-build', description: 'Build job name')
        string(name: 'JOB_INFRA', defaultValue: 'lab5-create-infra', description: 'Infrastructure job name')
        string(name: 'JOB_DEPLOY', defaultValue: 'lab4-deploy', description: 'Deploy job name')

        string(name: 'EXISTING_SERVER_ID', defaultValue: '7303c7d8-7ea6-4da5-ae91-f5560b03d742', description: 'Existing VM UUID (required for lab5 Terraform reuse)')
        string(name: 'EXISTING_SERVER_NAME', defaultValue: 'emeshkin-bot-vm', description: 'Existing VM name')
        string(name: 'EXISTING_SERVER_IP', defaultValue: '192.168.24.227', description: 'Existing VM private IP')
        string(name: 'VOLUME_SIZE_GB', defaultValue: '10', description: 'PostgreSQL data volume size')
    }

    stages {
        stage('Build') {
            steps {
                script {
                    def buildResult = build job: params.JOB_BUILD, wait: true, propagate: true
                    echo "Build completed: ${buildResult.fullDisplayName}"
                }
            }
        }

        stage('Create Infrastructure') {
            steps {
                script {
                    def infraResult = build(
                        job: params.JOB_INFRA,
                        wait: true,
                        propagate: true,
                        parameters: [
                            string(name: 'EXISTING_SERVER_ID', value: params.EXISTING_SERVER_ID),
                            string(name: 'EXISTING_SERVER_NAME', value: params.EXISTING_SERVER_NAME),
                            string(name: 'EXISTING_SERVER_IP', value: params.EXISTING_SERVER_IP),
                            string(name: 'VOLUME_SIZE_GB', value: params.VOLUME_SIZE_GB)
                        ]
                    )
                    echo "Infrastructure completed: ${infraResult.fullDisplayName}"
                }
            }
        }

        stage('Deploy') {
            steps {
                script {
                    def deployResult = build job: params.JOB_DEPLOY, wait: true, propagate: true
                    echo "Deploy completed: ${deployResult.fullDisplayName}"
                }
            }
        }
    }
}
