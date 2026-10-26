pipeline {
    agent {
        label 'admin-api-build'
    }

    options {
        skipDefaultCheckout(true)
        timeout(time: 30, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh '''
                    test -f build.gradle
                    test -f gradlew
                    echo 'GitHub checkout completed: Gradle project files found.'
                '''
            }
        }
        stage('Test') {
            steps {
                sh 'sh ./gradlew --no-daemon clean test'
            }
            post {
                always {
                    junit 'build/test-results/test/*.xml'
                }
            }
        }

        stage('Build JAR') {
            steps {
                sh 'sh ./gradlew --no-daemon bootJar'
            }
            post {
                success {
                    archiveArtifacts(
                        artifacts: 'build/libs/*.jar',
                        fingerprint: true
                    )
                }
            }
        }
        stage('Docker Build') {
            steps {
                sh '''
                    IMAGE="admin-api:aws-${BUILD_NUMBER}-$(git rev-parse --short=12 HEAD)"
                    docker build -t "$IMAGE" .
                '''
            }
        }    

        stage('Deploy') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'lightsail-ssh-key',
                        variable: 'LIGHTSAIL_SSH_KEY'
                    )
                ]) {
                    sh '''
                        set +x
                        IMAGE="admin-api:aws-${BUILD_NUMBER}-$(git rev-parse --short=12 HEAD)"
                        bash deploy/lightsail/jenkins-deploy.sh backend "$IMAGE"
                    '''
                }
            }
        }        

    }
}
