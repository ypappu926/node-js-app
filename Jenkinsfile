pipeline {
    agent any
    environment{
        AWS_ACCOUNT_ID = "917411321124"
        AWS_REGION = "ap-south-1"
        IMAGE_REPO_NAME = "node-app-repo"
        IMAGE_TAG="latest"
        REPOSITORY_URI= "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${IMAGE_REPO_NAME}"
    }
    stages {
        stage('Login into ECR') {
            steps {
                sh 'aws ecr get-login-password --region ${AWS_REGION} | docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com'
            }
        }
        
        stage('Git clone') {
            steps {
                checkout scmGit(branches: [[name: '*/master']], extensions: [], userRemoteConfigs: [[url: 'https://github.com/kariyaniamit/node-js-app.git']])
                //git 'https://github.com/kariyaniamit/node-js-app.git'
            }
        }
        
        stage('Docker Image build') {
            steps {
                script {
                    sh 'docker build -t ${IMAGE_REPO_NAME}:${IMAGE_TAG}'
                }
            }
        }
        
        stage('Docker Image push') {
            steps {
                script {
                    sh 'docker tag ${IMAGE_REPO_NAME}:latest ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${IMAGE_REPO_NAME}:${IMAGE_TAG}'
                    sh 'docker push ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_ACCOUNT_ID}.amazonaws.com/${IMAGE_REPO_NAME}:${IMAGE_TAG}'
                }
            }
        }
        
        
    }
}
