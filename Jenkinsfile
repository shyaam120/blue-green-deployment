pipeline {

    agent any

    environment {
        APP_NAME = 'blue-green-app'
        IMAGE_TAG = "jenkins-${BUILD_NUMBER}"

        BLUE_CONTAINER = 'blue'
        GREEN_CONTAINER = 'green'
        NGINX_CONTAINER = 'blue-green-nginx'

        DOCKER_NETWORK = 'blue-green-network'

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

                archiveArtifacts artifacts: 'app/target/*.jar',
                                 fingerprint: true
            }
        }

        stage('Docker Build') {
            steps {
                echo "Building Docker image: ${APP_NAME}:${IMAGE_TAG}"

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

        stage('Deploy Green') {
            steps {
                echo 'Deploying new version to Green environment...'

                sh """
                    docker rm -f ${GREEN_CONTAINER} 2>/dev/null || true

                    docker run -d \
                    --name ${GREEN_CONTAINER} \
                    --network ${DOCKER_NETWORK} \
                    -p ${GREEN_PORT}:8080 \
                    ${APP_NAME}:${IMAGE_TAG}
                """

                sh 'sleep 5'
            }
        }

        stage('Test Green') {
            steps {
                echo 'Testing Green environment...'

                sh """
                    curl -f http://localhost:${GREEN_PORT}
                """
            }
        }

        stage('Switch Nginx to Green') {
            steps {
                echo 'Switching Nginx traffic from Blue to Green...'

                sh '''
                    sed 's/proxy_pass http:\\/\\/blue;/proxy_pass http:\\/\\/green;/' \
                    nginx/nginx.conf > /tmp/nginx-green.conf

                    docker cp /tmp/nginx-green.conf \
                    ${NGINX_CONTAINER}:/etc/nginx/nginx.conf

                    docker exec ${NGINX_CONTAINER} nginx -t

                    docker exec ${NGINX_CONTAINER} nginx -s reload
                '''
            }
        }

        stage('Verify Deployment') {
            steps {
                echo 'Verifying traffic through Nginx...'

                sh """
                    curl -f http://localhost:${NGINX_PORT}
                """
            }
        }
    }

    post {

        success {
            echo '''
            ==========================================
            BLUE-GREEN DEPLOYMENT SUCCESSFUL
            ==========================================
            Maven build: SUCCESS
            Docker build: SUCCESS
            Green deployment: SUCCESS
            Nginx traffic switched to Green
            Blue environment remains available
            ==========================================
            '''
        }

        failure {
            echo '''
            ==========================================
            BLUE-GREEN DEPLOYMENT FAILED
            ==========================================
            Check the failed Jenkins stage.
            Blue environment was preserved.
            ==========================================
            '''
        }
    }
}
