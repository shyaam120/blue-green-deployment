pipeline {

    agent any

    environment {
        APP_NAME = 'blue-green-app'
        IMAGE_TAG = "jenkins-${BUILD_NUMBER}"

        BLUE_CONTAINER = 'blue'
        GREEN_CONTAINER = 'green'
        NGINX_CONTAINER = 'blue-green-nginx'

        DOCKER_NETWORK = 'blue-green-network'

        BLUE_PORT = '8081'
        GREEN_PORT = '8082'
        NGINX_PORT = '8080'
    }

    stages {

        stage('Checkout') {
            steps {
                echo 'Checking out source code from GitHub...'
                checkout scm
            }
        }

        stage('Detect Active Environment') {
            steps {
                script {

                    def activeConfig = sh(
                        script: """
                            docker exec ${NGINX_CONTAINER} \
                            nginx -T 2>/dev/null | \
                            grep 'proxy_pass http://'
                        """,
                        returnStdout: true
                    ).trim()

                    echo "Current Nginx routing:"
                    echo activeConfig

                    if (activeConfig.contains('proxy_pass http://blue;')) {
                        env.ACTIVE_COLOR = 'blue'
                        env.TARGET_COLOR = 'green'
                        env.TARGET_CONTAINER = GREEN_CONTAINER
                        env.TARGET_PORT = GREEN_PORT
                    }
                    else {
                        env.ACTIVE_COLOR = 'green'
                        env.TARGET_COLOR = 'blue'
                        env.TARGET_CONTAINER = BLUE_CONTAINER
                        env.TARGET_PORT = BLUE_PORT
                    }

                    echo "Active environment: ${env.ACTIVE_COLOR}"
                    echo "Target environment: ${env.TARGET_COLOR}"
                    echo "Target port: ${env.TARGET_PORT}"
                }
            }
        }

        stage('Maven Build') {
            steps {
                echo 'Building Java application with Maven...'

                dir('app') {
                    sh 'mvn clean package'
                }
            }
        }

        stage('Archive Maven Artifact') {
            steps {
                echo 'Archiving Maven JAR...'

                archiveArtifacts(
                    artifacts: 'app/target/*.jar',
                    fingerprint: true
                )
            }
        }

        stage('Docker Build') {
            steps {
                echo "Building Docker image ${APP_NAME}:${IMAGE_TAG}"

                sh """
                    docker build \
                    -t ${APP_NAME}:${IMAGE_TAG} \
                    .
                """
            }
        }

        stage('Docker Test') {
            steps {
                echo 'Testing Docker image...'

                sh """
                    docker run --rm \
                    ${APP_NAME}:${IMAGE_TAG} \
                    java -version
                """
            }
        }

        stage('Deploy Inactive Environment') {
            steps {
                echo "Deploying new version to ${env.TARGET_COLOR} environment..."

                sh """
                    docker rm -f ${TARGET_CONTAINER} 2>/dev/null || true

                    docker run -d \
                    --name ${TARGET_CONTAINER} \
                    --network ${DOCKER_NETWORK} \
                    -p ${TARGET_PORT}:8080 \
                    -e APP_VERSION=${BUILD_NUMBER} \
                    ${APP_NAME}:${IMAGE_TAG}
                """

                sh 'sleep 5'
            }
        }

        stage('Test Inactive Environment') {
            steps {
                echo "Testing ${env.TARGET_COLOR} environment..."

                sh """
                    curl -f http://localhost:${TARGET_PORT}

                    curl -f http://localhost:${TARGET_PORT} | \
                    grep "Version: ${BUILD_NUMBER}"
                """
            }
        }

        stage('Save Nginx Configuration') {
            steps {
                echo 'Saving current Nginx configuration...'

                sh """
                    rm -f /tmp/nginx-previous.conf

                    docker cp \
                    ${NGINX_CONTAINER}:/etc/nginx/nginx.conf \
                    /tmp/nginx-previous.conf
                """
            }
        }

        stage('Switch Traffic') {
            steps {
                echo "Switching Nginx traffic from ${env.ACTIVE_COLOR} to ${env.TARGET_COLOR}..."

                sh """
                    sed 's/proxy_pass http:\\/\\/blue;/proxy_pass http:\\/\\/${TARGET_COLOR};/' \
                    nginx/nginx.conf > /tmp/nginx-target.conf

                    sed -i 's/proxy_pass http:\\/\\/green;/proxy_pass http:\\/\\/${TARGET_COLOR};/' \
                    /tmp/nginx-target.conf

                    docker cp \
                    /tmp/nginx-target.conf \
                    ${NGINX_CONTAINER}:/etc/nginx/nginx.conf

                    docker exec \
                    ${NGINX_CONTAINER} \
                    nginx -t

                    docker exec \
                    ${NGINX_CONTAINER} \
                    nginx -s reload
                """
            }
        }

        stage('Verify Blue-Green Deployment') {
            steps {
                echo 'Verifying traffic through Nginx...'

                sh """
                    sleep 2

                    curl -f http://localhost:${NGINX_PORT}

                    curl -f http://localhost:${NGINX_PORT} | \
                    grep "Version: ${BUILD_NUMBER}"
                """
            }
        }
    }

    post {

        success {
            echo """
==================================================
BLUE-GREEN DEPLOYMENT SUCCESSFUL
==================================================

Build Number       : ${BUILD_NUMBER}
Active Environment : ${TARGET_COLOR}
Previous Environment: ${ACTIVE_COLOR}

Maven Build        : SUCCESS
Docker Build       : SUCCESS
Target Deployment  : SUCCESS
Target Test        : SUCCESS
Nginx Switch       : SUCCESS
Production Verify  : SUCCESS

Traffic is now running on ${TARGET_COLOR}.

The previous ${ACTIVE_COLOR} environment remains
available for rollback.

==================================================
"""
        }

        failure {
            echo """
==================================================
BLUE-GREEN DEPLOYMENT FAILED
==================================================

Attempting automatic rollback...

==================================================
"""

            sh """
                if [ -f /tmp/nginx-previous.conf ]; then

                    echo "Restoring previous Nginx configuration..."

                    docker cp \
                    /tmp/nginx-previous.conf \
                    ${NGINX_CONTAINER}:/etc/nginx/nginx.conf

                    docker exec \
                    ${NGINX_CONTAINER} \
                    nginx -t

                    docker exec \
                    ${NGINX_CONTAINER} \
                    nginx -s reload

                    echo "Rollback completed successfully."

                else

                    echo "No previous Nginx configuration found."
                    echo "Rollback was not required."

                fi
            """

            echo """
==================================================
ROLLBACK PROCESS FINISHED
Previous environment remains available.
==================================================
"""
        }

        always {
            sh """
                rm -f /tmp/nginx-previous.conf
                rm -f /tmp/nginx-target.conf
            """
        }
    }
}
