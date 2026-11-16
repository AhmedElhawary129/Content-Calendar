pipeline {
    agent any

    triggers {
        pollSCM('H/2 * * * *')
    }

    environment {
        IMAGE = 'ahmedelhawary1/content-calendar'
    }

    stages {
        stage('Check Trigger') {
            steps {
                script {
                    def msg = sh(script: 'git log -1 --pretty=%s', returnStdout: true).trim()
                    if (msg.startsWith('ci:')) {
                        env.SKIP_BUILD = 'true'
                        currentBuild.description = 'Skipped: commit made by Jenkins'
                    }
                }
            }
        }

        stage('CI/CD') {
            when { expression { env.SKIP_BUILD != 'true' } }
            stages {

                stage('Test') {
                    steps {
                        sh 'chmod +x mvnw'
                        sh './mvnw -B test'
                    }
                    post {
                        always {
                            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                        }
                    }
                }

                stage('Build') {
                    steps {
                        sh './mvnw -B package -DskipTests'
                    }
                }

                stage('Docker Build & Push') {
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'dockerhub',
                                                          usernameVariable: 'DH_USER',
                                                          passwordVariable: 'DH_PASS')]) {
                            sh '''
                                echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                                docker build -t $IMAGE:$BUILD_NUMBER .
                                docker push $IMAGE:$BUILD_NUMBER
                            '''
                        }
                    }
                }

                stage('Update Manifest') {
                    steps {
                        withCredentials([usernamePassword(credentialsId: 'github',
                                                          usernameVariable: 'GH_USER',
                                                          passwordVariable: 'GH_TOKEN')]) {
                            sh '''
                                sed -i "s|image: $IMAGE:.*|image: $IMAGE:$BUILD_NUMBER|" k8s/06-app-deployment.yaml
                                git config user.email "jenkins@local"
                                git config user.name "Jenkins"
                                git add k8s/06-app-deployment.yaml
                                git commit -m "ci: update image to $BUILD_NUMBER"
                                git push https://$GH_USER:$GH_TOKEN@github.com/AhmedElhawary129/Content-Calendar.git HEAD:main
                            '''
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            archiveArtifacts artifacts: 'target/*.jar', fingerprint: true, allowEmptyArchive: true
        }
    }
}