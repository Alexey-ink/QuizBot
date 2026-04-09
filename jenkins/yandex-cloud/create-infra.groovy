// Создание инфраструктуры + базовая настройка ВМ
// Артефакты на выходе: server_ip.txt, disk_id.txt, server_name.txt
pipeline {
    agent {
        label 'shihalev'  // Агент с установленными terraform, ansible, yc-cli
    }

    options {
        timeout(time: 10, unit: 'MINUTES')  
        disableConcurrentBuilds()          
    }

    environment {
        TF_IN_AUTOMATION = "1"
        TF_INPUT = "0"
        TF_DIR = "infrastructure/terraform"
        
        ANSIBLE_HOST_KEY_CHECKING = "False"
        ANSIBLE_DIR = "jenkins/ansible"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                echo "✅ Code checked out: ${env.GIT_COMMIT?.take(5)}"
            }
        }

        stage('🔍 Validate & Format') {
            steps {
                dir("${env.TF_DIR}") {
                    sh 'terraform fmt -check -recursive || terraform fmt -recursive'
                    sh 'terraform init -backend=true -reconfigure'
                    sh 'terraform validate'
                }
                dir("${env.ANSIBLE_DIR}") {
                    sh 'ansible-playbook --syntax-check playbook.yml'
                }
                echo "✅ Validation passed"
            }
        }

        stage('Terraform: Provision Infrastructure') {
            steps {
                withCredentials([
                    string(credentialsId: 'yc-token', variable: 'YC_TOKEN'),
                    string(credentialsId: 'ssh-public-key', variable: 'SSH_PUB_KEY')
                ]) {
                    dir("${env.TF_DIR}") {
                        sh "terraform init -reconfigure"

                        echo "📋 Running terraform plan..."                        
                        sh """
                            terraform plan -out=tfplan \\
                                -var='yc_token=${YC_TOKEN}' \\
                                -var='ssh_public_key=${SSH_PUB_KEY}' \\
                        """

                        echo "🚀 Applying terraform plan..."
                        script {
                            sh "terraform apply -auto-approve tfplan"      
                        }
                            
                        sh """
                            terraform output -raw server_public_ip > ${env.WORKSPACE}/server_ip.txt
                            terraform output -raw postgres_disk_id > ${env.WORKSPACE}/disk_id.txt
                            terraform output -raw server_name > ${env.WORKSPACE}/server_name.txt
                        """
                        }
                    }
                }
            }
            post {
                success {
                    script {
                        def ip = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                        echo "✅ Infrastructure ready: ${ip}"
                        currentBuild.description = "Server: ${ip}"
                    }
                }
            }
        }

        stage('⏳ Wait for SSH') {
            steps {
                script {
                    def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                    echo "⏳ Waiting for SSH on ${serverIP}:22..."
                    
                    retry(20) {
                        sleep 10
                        sh "nc -z -w 5 ${serverIP} 22"
                    }
                    echo "✅ SSH is accessible"
                }
            }
            post {
                failure {
                    echo "❌ SSH not available after timeout. Check VM status in YC Console."
                    error("SSH wait failed")
                }
            }
        }

        stage('⚙️ Ansible: Configure VM') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'ssh-private-key',
                        keyFileVariable: 'SSH_KEY_PATH',
                        usernameVariable: 'SSH_USER'
                    ),
                    string(credentialsId: 'yc-token', variable: 'YC_TOKEN')
                ]) {
                    script {
                        def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                        def diskId = readFile(file: "${env.WORKSPACE}/disk_id.txt").trim()
                        
                        echo "🔧 Configuring ${serverIP} with Ansible..."
                        
                        dir("${env.ANSIBLE_DIR}") {
                            sh """
                                chmod 600 ${SSH_KEY_PATH} && \\
                                ansible-playbook -i inventory.yml playbook.yml \\
                                    --extra-vars "server_ip=${serverIP}" \\
                                    --extra-vars "postgres_disk_id=${diskId}" \\
                                    --extra-vars "ansible_ssh_private_key_file=${SSH_KEY_PATH}" \\
                                    --extra-vars "ansible_user=${SSH_USER}" \\
                                    -v
                            """
                        }
                    }
                }
            }
            post {
                success {
                    echo "✅ Ansible configuration completed"
                }
                failure {
                    echo "❌ Ansible failed! VM may be partially configured."
                }
            }
        }

        stage('🧪 Post-Configuration Checks') {
            steps {
                script {
                    def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                    withCredentials([sshUserPrivateKey(credentialsId: 'ssh-private-key', keyFileVariable: 'SSH_KEY_PATH')]) {
                        sh """
                            ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no -o ConnectTimeout=10 ubuntu@${serverIP} \\
                                'docker --version && docker-compose --version'
                        """
                        sh """
                            ssh -i ${SSH_KEY_PATH} -o StrictHostKeyChecking=no ubuntu@${serverIP} \\
                                'test -d /data/postgres && echo "✅ Postgres mount point ready" || echo "⚠️ Mount point not found"'
                        """
                    }
                }
            }
            post {
                failure {
                    echo "⚠️ Post-checks failed, but continuing. Verify manually."
                }
            }
        }
    }

    post {
        always {
            // Архивируем артефакты для downstream-пайплайнов
            archiveArtifacts(
                artifacts: 'server_ip.txt,disk_id.txt,server_name.txt,postgres_conn.txt',
                allowEmptyArchive: false,
                fingerprint: true
            )
            
            echo "📦 Artifacts archived. Build #${env.BUILD_NUMBER} finished."
        }
        success {
            script {
                def ip = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                echo "🎉 create-infra completed successfully!"
                echo "🌐 Server IP: ${ip}"
            }
        }
        failure {
            echo "💥 create-infra FAILED!"
            echo "🔍 Check console output and YC Console for details."
        }
        unstable {
            echo "⚠️ Pipeline completed with warnings. Review post-checks."
        }
    }
}