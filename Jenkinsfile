pipeline {
    agent any

    environment {
        SONARQUBE = 'SonarQubeServer'               // Name from Jenkins global config
        ARTIFACTORY_SERVER_ID = 'ArtifactoryServer' // Server ID from Jenkins Artifactory config
        AWS_REGION = 'us-east-1'                    // AWS region
        ECR_REPO = '116099575554.dkr.ecr.us-east-1.amazonaws.com/myapp'
        GIT_REPO_HTTPS = 'https://github.com/Darshancr9/assign_anil.git'
        GIT_BRANCH = 'main'
        K8S_PATH = 'k8s'                            // path ArgoCD watches
    }

    stages {

        stage('Checkout Code') {
            steps {
                // Checkout with Jenkins' credentials configured earlier (github-creds)
                git branch: "${GIT_BRANCH}", credentialsId: 'github-creds', url: "${GIT_REPO_HTTPS}"
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
                    // Use immutable tag (build number) for reproducibility
                    def TAG = "${env.BUILD_NUMBER}"
                    withAWS(region: "${AWS_REGION}", credentials: 'aws-creds') {
                        sh """
                            set -e

                            echo "🔹 Logging in to AWS ECR..."
                            aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${ECR_REPO}

                            echo "🔹 Building Docker image (tag: ${TAG})..."
                            docker build -t myapp:${TAG} .

                            echo "🔹 Tagging images for ECR..."
                            docker tag myapp:${TAG} ${ECR_REPO}:${TAG}
                            docker tag myapp:${TAG} ${ECR_REPO}:latest

                            echo "🔹 Pushing images to ECR..."
                            docker push ${ECR_REPO}:${TAG}
                            docker push ${ECR_REPO}:latest
                        """
                    }
                }
            }
        }

        stage('Update K8s Manifests & Push (so ArgoCD deploys)') {
            steps {
                script {
                    // Use the build number as the image tag we will inject into manifests
                    def NEW_TAG = "${env.BUILD_NUMBER}"
                    def IMAGE_WITH_TAG = "${ECR_REPO}:${NEW_TAG}"

                    // Ensure k8s folder and files exist (safe for beginners)
                    sh """
                        set -e

                        # create k8s directory if missing
                        if [ ! -d "${K8S_PATH}" ]; then
                          echo "⚙️  Creating ${K8S_PATH}/ with minimal manifests"
                          mkdir -p ${K8S_PATH}
                        fi

                        # create deployment.yaml if missing
                        if [ ! -f "${K8S_PATH}/deployment.yaml" ]; then
cat > ${K8S_PATH}/deployment.yaml <<'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  labels:
    app: myapp
spec:
  replicas: 2
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
    spec:
      imagePullSecrets:
      - name: regcred
      containers:
      - name: myapp
        image: REPLACE_IMAGE
        ports:
        - containerPort: 8080
        readinessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
        livenessProbe:
          httpGet:
            path: /
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 20
EOF
                        fi

                        # create service.yaml if missing
                        if [ ! -f "${K8S_PATH}/service.yaml" ]; then
cat > ${K8S_PATH}/service.yaml <<'EOF'
apiVersion: v1
kind: Service
metadata:
  name: myapp-service
spec:
  type: ClusterIP
  selector:
    app: myapp
  ports:
    - port: 80
      targetPort: 8080
EOF
                        fi

                        # create kustomization.yaml if missing
                        if [ ! -f "${K8S_PATH}/kustomization.yaml" ]; then
cat > ${K8S_PATH}/kustomization.yaml <<'EOF'
apiVersion: kustomize.config.k8s.io/v1
kind: Kustomization
resources:
  - deployment.yaml
  - service.yaml
images:
  - name: ${ECR_REPO}
    newTag: PLACEHOLDER
EOF
                        fi

                        # update the deployment image and kustomization image tag
                        # replace REPLACE_IMAGE in deployment.yaml if present
                        if grep -q "REPLACE_IMAGE" ${K8S_PATH}/deployment.yaml 2>/dev/null; then
                          sed -i "s#REPLACE_IMAGE#${IMAGE_WITH_TAG}#g" ${K8S_PATH}/deployment.yaml
                        fi

                        # update the kustomization.yaml newTag (fallback)
                        sed -i "s#newTag: .*#newTag: ${NEW_TAG}#g" ${K8S_PATH}/kustomization.yaml || true

                        # show the updated files for debug
                        echo "----- ${K8S_PATH}/deployment.yaml -----"
                        sed -n '1,200p' ${K8S_PATH}/deployment.yaml || true
                        echo "----- ${K8S_PATH}/kustomization.yaml -----"
                        sed -n '1,200p' ${K8S_PATH}/kustomization.yaml || true
                    """
                    // Commit and push the manifest change back to Git so ArgoCD picks it up
                    // We use username/password credentials from Jenkins (github-creds). The credentials must allow push.
                    withCredentials([usernamePassword(credentialsId: 'github-creds', usernameVariable: 'GIT_USER', passwordVariable: 'GIT_PASS')]) {
                        sh """
                            set -e
                            # configure git for commit
                            git config user.email "jenkins@yourdomain"
                            git config user.name "jenkins"

                            # make sure we are on the right branch
                            git checkout ${GIT_BRANCH} || true
                            git pull origin ${GIT_BRANCH} || true

                            # replace remote origin to include credentials for push
                            # NOTE: this will expose the token in process list for a short time on some systems;
                            # if you prefer, configure SSH key (sshagent) and remove these lines.
                            REMOTE_URL="https://${GIT_USER}:${GIT_PASS}@github.com/Darshancr9/assign_anil.git"
                            git remote set-url origin "${REMOTE_URL}"

                            git add ${K8S_PATH}/deployment.yaml ${K8S_PATH}/kustomization.yaml ${K8S_PATH}/service.yaml || true
                            git commit -m "ci: bump myapp image to ${NEW_TAG} by Jenkins #${env.BUILD_NUMBER}" || echo "No manifest changes to commit"
                            git push origin ${GIT_BRANCH} || echo "Push failed - ensure credentials are correct"
                            
                            # reset origin to HTTPS without creds (optional cleanup)
                            git remote set-url origin "${GIT_REPO_HTTPS}"
                        """
                    }
                }
            }
        }

        stage('Optional: Trigger ArgoCD Sync (if argocd CLI configured)') {
            when {
                expression { return true } // keep true but step is guarded - if argocd CLI missing it will just print a message
            }
            steps {
                script {
                    // This block will try to run argocd CLI if installed on agent.
                    // If you do not have argocd CLI on Jenkins agent, this will simply print a helpful message.
                    sh '''
                      if command -v argocd >/dev/null 2>&1; then
                        echo "🔹 argocd CLI present. Attempting to login and sync (requires ARGOCD_SERVER & ARGOCD_TOKEN env or credentials)."
                        # If you have ARGOCD_SERVER and ARGOCD_TOKEN as credentials in Jenkins, you can use them here.
                        # Example usage (uncomment and set env vars in Jenkins if you want automatic sync):
                        # argocd login ${ARGOCD_SERVER} --username admin --password ${ARGOCD_PASSWORD} --insecure
                        # argocd app sync myapp
                        echo "⚠️ Customize the argocd login & sync commands in the Jenkinsfile to automatically trigger ArgoCD."
                      else
                        echo "⚪ argocd CLI not found on this agent. Skipping explicit sync. ArgoCD should auto-sync if configured with automated sync."
                      fi
                    '''
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

