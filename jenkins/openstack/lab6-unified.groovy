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

        string(name: 'SERVER_NAME', defaultValue: 'emeshkin-bot-vm', description: 'VM name: reuse if exists, otherwise create new')
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'OpenStack image name (used when creating new VM)')
        string(name: 'FLAVOR_NAME', defaultValue: '', description: 'OpenStack flavor name (used when creating new VM)')
        string(name: 'NETWORK_ID', defaultValue: '', description: 'OpenStack network UUID (used when creating new VM)')
        string(name: 'KEY_PAIR', defaultValue: '', description: 'OpenStack keypair (used when creating new VM)')
        string(name: 'SECURITY_GROUP', defaultValue: 'default', description: 'Security group (used when creating new VM)')
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
                            string(name: 'SERVER_NAME', value: params.SERVER_NAME),
                            string(name: 'IMAGE_NAME', value: params.IMAGE_NAME),
                            string(name: 'FLAVOR_NAME', value: params.FLAVOR_NAME),
                            string(name: 'NETWORK_ID', value: params.NETWORK_ID),
                            string(name: 'KEY_PAIR', value: params.KEY_PAIR),
                            string(name: 'SECURITY_GROUP', value: params.SECURITY_GROUP),
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
