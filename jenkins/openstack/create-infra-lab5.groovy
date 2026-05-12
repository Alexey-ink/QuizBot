pipeline {
    agent { label 'emeshkin' }

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    environment {
        TF_IN_AUTOMATION = '1'
        TF_INPUT = '0'
        TF_DIR = 'infrastructure/terraform'
        TF_CLI_CONFIG_FILE = '/home/ubuntu/.terraformrc'
        ANSIBLE_DIR = 'jenkins/ansible'
        ANSIBLE_HOST_KEY_CHECKING = 'False'
    }

    parameters {
        string(name: 'SERVER_NAME', defaultValue: 'emeshkin-bot-vm', description: 'VM name: reuse if exists, otherwise create new')
        string(name: 'IMAGE_NAME', defaultValue: '', description: 'OpenStack image name (used when creating new VM)')
        string(name: 'FLAVOR_NAME', defaultValue: '', description: 'OpenStack flavor name (used when creating new VM)')
        string(name: 'NETWORK_ID', defaultValue: '', description: 'OpenStack network UUID (used when creating new VM)')
        string(name: 'KEY_PAIR', defaultValue: '', description: 'OpenStack keypair (used when creating new VM)')
        string(name: 'SECURITY_GROUP', defaultValue: 'default', description: 'Security group (used when creating new VM)')
        string(name: 'VOLUME_SIZE_GB', defaultValue: '10', description: 'PostgreSQL data volume size')
        booleanParam(
            name: 'DEPLOY_QUIZBOT',
            defaultValue: true,
            description: 'После Ansible: PostgreSQL + Temurin 23 + JAR + quizbot.service на ВМ (как ЛР3)'
        )
        string(name: 'DB_NAME', defaultValue: 'quizbot', description: 'Имя БД на ВМ (для quizbot.env)')
        string(name: 'DB_USER', defaultValue: 'quizbot', description: 'Пользователь БД')
        string(name: 'DB_PASS', defaultValue: 'quizbot_pass', description: 'Пароль БД (только для лабы)')
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Terraform Init/Validate') {
            steps {
                dir("${env.TF_DIR}") {
                    sh 'terraform fmt -check -recursive || terraform fmt -recursive'
                    sh 'terraform init -reconfigure'
                    sh 'terraform validate'
                }
            }
        }

        stage('Terraform Plan/Apply') {
            steps {
                script {
                    // Параметры из Jenkinsfile не всегда попадают в env shell при первом запуске / смешанной конфигурации job.
                    def pServer = (params.SERVER_NAME ?: 'emeshkin-bot-vm').toString().trim()
                    def pImage = (params.IMAGE_NAME ?: '').toString().trim()
                    def pFlavor = (params.FLAVOR_NAME ?: '').toString().trim()
                    def pNet = (params.NETWORK_ID ?: '').toString().trim()
                    def pKey = (params.KEY_PAIR ?: '').toString().trim()
                    def pSg = (params.SECURITY_GROUP ?: 'default').toString().trim()
                    def pVol = (params.VOLUME_SIZE_GB ?: '10').toString().trim()

                    withEnv([
                        "SERVER_NAME=${pServer}",
                        "IMAGE_NAME=${pImage}",
                        "FLAVOR_NAME=${pFlavor}",
                        "NETWORK_ID=${pNet}",
                        "KEY_PAIR=${pKey}",
                        "SECURITY_GROUP=${pSg}",
                        "VOLUME_SIZE_GB=${pVol}",
                    ]) {
                        withCredentials([
                            string(credentialsId: 'openstack-student-password', variable: 'OS_PASSWORD'),
                            file(credentialsId: 'emeshkin-openrc', variable: 'OPENRC_FILE')
                        ]) {
                            dir("${env.TF_DIR}") {
                                sh '''
                                    set -eu

                                    # Load all OS_* except password from openrc credential file
                                    while IFS= read -r line; do
                                      case "$line" in
                                        export\\ OS_PASSWORD=*|export\\ OS_PASSWORD_INPUT=*) continue ;;
                                        export\\ OS_*=*) eval "${line#export }" ;;
                                      esac
                                    done < "${OPENRC_FILE}"

                                    export OS_PASSWORD="${OS_PASSWORD}"
                                    # openrc уже задаёт .../v3; не дублировать суффикс /v3
                                    _auth="${OS_AUTH_URL%/}"
                                    case "${_auth}" in */v3) export TF_VAR_auth_url="${_auth}";; *) export TF_VAR_auth_url="${_auth}/v3";; esac
                                    export TF_VAR_username="${OS_USERNAME}"
                                    export TF_VAR_password="${OS_PASSWORD}"
                                    export TF_VAR_project_name="${OS_PROJECT_NAME}"
                                    export TF_VAR_user_domain_name="${OS_USER_DOMAIN_NAME:-Default}"
                                    export TF_VAR_project_domain_name="${OS_PROJECT_DOMAIN_NAME:-Default}"
                                    export TF_VAR_region="${OS_REGION_NAME:-RegionOne}"

                                    SERVER_NAME_TRIMMED="$(echo "${SERVER_NAME}" | xargs)"
                                    test -n "${SERVER_NAME_TRIMMED}"

                                    EXISTING_ID="$(openstack server list --name "^${SERVER_NAME_TRIMMED}$" -f value -c ID | head -n 1 || true)"
                                    EXISTING_IP=""

                                    if [ -n "${EXISTING_ID}" ]; then
                                        echo "✅ Existing VM found: ${SERVER_NAME_TRIMMED} (${EXISTING_ID})"
                                        EXISTING_IP="$(openstack server show "${EXISTING_ID}" -f json -c addresses | python3 -c 'import json,sys; d=json.load(sys.stdin).get("addresses",{}); ips=[ip for arr in d.values() for ip in arr if "." in ip]; print(ips[0] if ips else "")' || true)"
                                        export TF_VAR_existing_server_id="${EXISTING_ID}"
                                        export TF_VAR_existing_server_name="${SERVER_NAME_TRIMMED}"
                                        export TF_VAR_existing_server_ip="${EXISTING_IP}"
                                    else
                                        echo "ℹ️ Existing VM not found, creating a new one: ${SERVER_NAME_TRIMMED}"
                                        test -n "${IMAGE_NAME}"
                                        test -n "${FLAVOR_NAME}"
                                        test -n "${NETWORK_ID}"
                                        test -n "${KEY_PAIR}"
                                        export TF_VAR_existing_server_id=""
                                        export TF_VAR_existing_server_name="${SERVER_NAME_TRIMMED}"
                                        export TF_VAR_existing_server_ip=""
                                        export TF_VAR_image_name="${IMAGE_NAME}"
                                        export TF_VAR_flavor_name="${FLAVOR_NAME}"
                                        export TF_VAR_network_id="${NETWORK_ID}"
                                        export TF_VAR_key_pair="${KEY_PAIR}"
                                        export TF_VAR_security_group="${SECURITY_GROUP}"
                                    fi

                                    export TF_VAR_volume_size_gb="${VOLUME_SIZE_GB}"

                                    terraform plan -out=tfplan
                                    terraform apply -auto-approve tfplan

                                    terraform output -raw server_ip > "${WORKSPACE}/server_ip.txt"
                                    terraform output -raw server_name > "${WORKSPACE}/server_name.txt"
                                    terraform output -raw postgres_volume_id > "${WORKSPACE}/volume_id.txt"
                                '''
                            }
                        }
                    }
                }
            }
        }

        stage('Wait for SSH') {
            steps {
                script {
                    def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                    retry(20) {
                        sleep 10
                        sh "nc -z -w 5 ${serverIP} 22"
                    }
                }
            }
        }

        stage('Ansible Configure') {
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'emeshkin-ssh',
                        keyFileVariable: 'SSH_KEY_PATH',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    script {
                        def serverIP = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                        dir("${env.ANSIBLE_DIR}") {
                            sh """
                                set -eu
                                chmod 600 "${SSH_KEY_PATH}"
                                ansible-playbook -i inventory.yml playbook.yml \
                                  --extra-vars "server_ip=${serverIP}" \
                                  --extra-vars "ansible_ssh_private_key_file=${SSH_KEY_PATH}" \
                                  --extra-vars "ansible_user=${SSH_USER}"
                            """
                        }
                    }
                }
            }
        }

        stage('Deploy QuizBot (как ЛР3)') {
            when {
                expression {
                    def v = params.DEPLOY_QUIZBOT
                    return v == null || v == true || v.toString().equalsIgnoreCase('true')
                }
            }
            steps {
                withCredentials([
                    sshUserPrivateKey(
                        credentialsId: 'emeshkin-ssh',
                        keyFileVariable: 'SSH_KEY_PATH',
                        usernameVariable: 'SSH_USER'
                    )
                ]) {
                    script {
                        def vmIp = readFile(file: "${env.WORKSPACE}/server_ip.txt").trim()
                        sh """
                            set -eu
                            chmod 600 "\${SSH_KEY_PATH}"
                            export WORKSPACE="\${WORKSPACE}"
                            export VM_IP='${vmIp}'
                            export SSH_KEY_PATH="\${SSH_KEY_PATH}"
                            export SSH_USER="\${SSH_USER}"
                            export DB_NAME='${params.DB_NAME}'
                            export DB_USER='${params.DB_USER}'
                            export DB_PASS='${params.DB_PASS}'
                            bash "\${WORKSPACE}/jenkins/openstack/lab5-deploy-quizbot-on-vm.sh"
                        """
                    }
                }
            }
        }
    }

    post {
        always {
            archiveArtifacts artifacts: 'server_ip.txt,server_name.txt,volume_id.txt', allowEmptyArchive: false
        }
    }
}
