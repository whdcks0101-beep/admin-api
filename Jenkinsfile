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
                    docker build -t "admin-api:ci-${BUILD_NUMBER}" .
                '''
            }
        }    

        stage('Deploy') {
            steps {
                withCredentials([
                    file(
                        credentialsId: 'k8s-admin-portfolio',
                        variable: 'KUBECONFIG'
                    )
                ]) {
                    sh '''
                        kubectl -n admin-portfolio set image \
                            deployment/backend \
                            backend="admin-api:ci-${BUILD_NUMBER}"

                        kubectl -n admin-portfolio rollout status \
                            deployment/backend \
                            --timeout=180s
                    '''
                }
            }
        }        

    }
}
