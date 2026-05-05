pipeline {
    agent { label 'emeshkin' }

    options {
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    parameters {
        string(name: 'JOB_BUILD', defaultValue: 'java-build', description: 'Build job name')
        string(name: 'JOB_INFRA', defaultValue: 'lab5-create-infra', description: 'Infrastructure job name')
        string(name: 'JOB_DEPLOY', defaultValue: 'lab4-deploy', description: 'Deploy job name')

        string(name: 'NETWORK_ID', defaultValue: '6a69f855-8a2d-4994-baf0-ed4feedd897b', description: 'OpenStack network UUID')
        string(name: 'IMAGE_NAME', defaultValue: 'ubuntu-24.04', description: 'OpenStack image name')
        string(name: 'FLAVOR_NAME', defaultValue: 'm1.small', description: 'OpenStack flavor name')
        string(name: 'SERVER_NAME', defaultValue: 'emeshkin-bot-vm', description: 'VM name')
        string(name: 'KEYPAIR_NAME', defaultValue: 'emeshkin-key', description: 'OpenStack keypair name')
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
                            string(name: 'NETWORK_ID', value: params.NETWORK_ID),
                            string(name: 'IMAGE_NAME', value: params.IMAGE_NAME),
                            string(name: 'FLAVOR_NAME', value: params.FLAVOR_NAME),
                            string(name: 'SERVER_NAME', value: params.SERVER_NAME),
                            string(name: 'KEYPAIR_NAME', value: params.KEYPAIR_NAME),
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
