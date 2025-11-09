pipeline {
    agent any

    environment {
        SONARQUBE = 'SonarQubeServer'               // Name from Jenkins global config
        ARTIFACTORY_SERVER_ID = 'ArtifactoryServer' // Server ID from Jenkins Artifactory config
        AWS_REGION = 'us-east-1'                    // AWS region
        ECR_REPO = '116099575554.dkr.ecr.us-east-1.amazonaws.com/myapp'
    }

    stages {

        stage('Checkout Code') {
            steps {
                git branch: 'main', credentialsId: 'github-creds', url: 'https://github.com/Darshancr9/assign_anil.git'
            }
        }

        stage('Code Scan - SonarQube') {
            steps {
                withSonarQubeEnv("${SONARQUBE}") {
                    sh 'mvn clean verify sonar:sonar -Dsonar.projectKey=myapp -Dsonar.projectName=myapp'
                }
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Upload Artifact to JFrog Artifactory') {
            steps {
                script {
                    // Connect to the configured Artifactory server
                    def server = Artifactory.server("${ARTIFACTORY_SERVER_ID}")

                    // Collect build information
                    def buildInfo = Artifactory.newBuildInfo()
                    buildInfo.env.capture = true

                    // Define upload spec (target repo path)
                    def uploadSpec = """{
                        "files": [
                            {
                                "pattern": "target/*.jar",
                                "target": "libs-release-local/myapp/"
                            }
                        ]
                    }"""

                    // Upload artifact to Artifactory
                    server.upload(spec: uploadSpec, buildInfo: buildInfo)

                    // Publish build info to Artifactory
                    server.publishBuildInfo(buildInfo)
                }
            }
        }

        stage('Docker Build & Push to ECR') {
            steps {
                script {
                    withAWS(region: "${AWS_REGION}", credentials: 'aws-creds') {
                        sh '''
                            echo "🔹 Logging in to AWS ECR..."
                            aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REPO}

                            echo "🔹 Building Docker image..."
                            docker build -t myapp .

                            echo "🔹 Tagging image for ECR..."
                            docker tag myapp:latest ${ECR_REPO}:latest

                            echo "🔹 Pushing image to ECR..."
                            docker push ${ECR_REPO}:latest
                        '''
                    }
                }
            }
        }
    }

    post {
        success {
            echo '✅ Build and deployment pipeline completed successfully!'
        }
        failure {
            echo '❌ Build failed! Check logs for details.'
        }
    }
}
