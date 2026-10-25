pipeline {
    agent any

    options {
        skipDefaultCheckout(true)
        timeout(time: 10, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '10'))
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
    }
}
