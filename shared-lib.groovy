import org.ans.Constants
def servicePath1 = ''
def call(String projectType='WithParent', String servicePath, int port, String requestsCpu, String requestsMemory, int minReplicas, int maxReplicas,String healthcheckPath='') {
servicePath1 = 	servicePath

def SonarClear = true
def SonarDashboardURL = ''
def SonarStatus = Constants.SonarStatus.ByPassed
def BuildSuccessfully = false

def mainJobParts = JOB_NAME.tokenize('/') as String[]  // Multibranch project return project name and branch togather in JOB_NAME so to remove branch name I forced to do this. Generally JOB_NAME will be enough to get project name if it is simple project.
def mainJobName = mainJobParts[0]

def buildMailRequire = ''
def deploymentMailRequire = ''
def buildTeamsNotificationRequire = ''
def deploymentTeamsNotificationRequire = ''
def serverName = ''
def deployFileName = ''
def jenkinsFolderPath =''
def deploymentServerDetails = ''
def deploymentServerPwd = '' 
	
def sourcePath=''
def additionFolderName=''
def attachedLog=true
	
def sourceDockerfile = ''
def jarName = ''
def dockersh = ''


	switch(BRANCH_NAME.toUpperCase()){
		case 'DEVELOPMENT' :
		case 'SIDBI-DEVELOPMENT' :
		case 'DEVELOPMENT-ANS' :
			serverName = Constants.ANS_SIT_BRANCH_SERVERNAME
			buildMailRequire  = Constants.ANS_SIT_BUILD_MAIL_REQUIRE
			deploymentMailRequire  = Constants.ANS_SIT_DEPLOY_MAIL_REQUIRE
			buildTeamsNotificationRequire  = Constants.ANS_SIT_BUILD_TEAMS_NOTIFICATION_REQUIRE
			deploymentTeamsNotificationRequire  = Constants.ANS_SIT_DEPLOY_TEAMS_NOTIFICATION_REQUIRE
			deployFileName = Constants.ANS_SIT_DEPLOY_FILENAME
			jenkinsFolderPath = Constants.ANS_SIT_SERVER_JENKINPATH
			deploymentServerDetails = ANS_SIT_Server
			deploymentServerPwd = ANS_SIT_Pwd
			deploymentServerIP = ANS_SIT_Server_IP
			
			break;
		
		case 'QA' :
			serverName = Constants.ANS_QA_BRANCH_SERVERNAME
			buildMailRequire  = Constants.ANS_QA_BUILD_MAIL_REQUIRE
			deploymentMailRequire  = Constants.ANS_QA_DEPLOY_MAIL_REQUIRE
			buildTeamsNotificationRequire  = Constants.ANS_QA_BUILD_TEAMS_NOTIFICATION_REQUIRE
			deploymentTeamsNotificationRequire  = Constants.ANS_QA_DEPLOY_TEAMS_NOTIFICATION_REQUIRE
			deployFileName = Constants.ANS_QA_DEPLOY_FILENAME
			jenkinsFolderPath = Constants.ANS_QA_SERVER_JENKINPATH
			deploymentServerDetails = ANS_QA_Server
			deploymentServerPwd = ANS_QA_Pwd
			deploymentServerIP = ANS_QA_Server_IP
				
			break;
		case 'RELEASE' :
			serverName = Constants.ANS_RELEASE_BRANCH_SERVERNAME
			buildMailRequire = Constants.ANS_UAT_BUILD_MAIL_REQUIRE
			deploymentMailRequire = Constants.ANS_UAT_DEPLOY_MAIL_REQUIRE
			buildTeamsNotificationRequire  = Constants.ANS_UAT_BUILD_TEAMS_NOTIFICATION_REQUIRE
			deploymentTeamsNotificationRequire  = Constants.ANS_UAT_DEPLOY_TEAMS_NOTIFICATION_REQUIRE
			deployFileName = Constants.ANS_UAT_DEPLOY_FILENAME
			jenkinsFolderPath = Constants.ANS_UAT_SERVER_JENKINPATH
			deploymentServerDetails = ANS_UAT_Server
			deploymentServerPwd = ANS_UAT_Pwd
			deploymentServerIP = ANS_UAT_Server_IP
		 	deploymentServerIPK8S = ANS_K8SMASTER_Server_IP
		
			break;
		case 'MASTER' :
			serverName = Constants.ANS_MASTER_BRANCH_SERVERNAME
			buildMailRequire = Constants.ANS_PROD_BUILD_MAIL_REQUIRE
			deploymentMailRequire = Constants.ANS_PROD_DEPLOY_MAIL_REQUIRE
			buildTeamsNotificationRequire  = Constants.ANS_PROD_BUILD_TEAMS_NOTIFICATION_REQUIRE
			deploymentTeamsNotificationRequire  = Constants.ANS_PROD_DEPLOY_TEAMS_NOTIFICATION_REQUIRE
			deployFileName = Constants.ANS_PROD_DEPLOY_FILENAME
			jenkinsFolderPath = Constants.ANS_PROD_SERVER_JENKINPATH
			deploymentServerDetails = ANS_PROD_Server
			deploymentServerPwd = ANS_PROD_Pwd
			deploymentServerIP = ANS_PROD_Server_IP
			break;
	}
	

    pipeline {
        agent any
	//options {                 
	//	skipDefaultCheckout true   //Multibranch will automatically pull data from repository at start so if wants to ignore it and wants to do it manually then this line is useful.
	//    }
	//triggers { cron('H/5 * * * *') }
	    triggers {pollSCM('H/2 * * * *')}
	options {
    		buildDiscarder(logRotator(numToKeepStr: '10'))
  	}
        tools {
            // Install the Maven version configured as "M3" and add it to the path.
            jdk "JDK"
            maven "Maven"
        }
	
        stages {
            //stage('checkout'){
            //    steps {
            //        git url: "${env.ANS_GIT_URL}/${env.JOB_NAME}.git", branch: "${env.BRANCH_NAME}", credentialsId: 'newID'
                
            //    }
            //}
            stage ("SonarQube analysis") {
                when {
                    expression {"${env.BRANCH_NAME}".toUpperCase() == Constants.ANS_SONAR_PERFORM_BRANCH.toUpperCase() && Constants.ANS_SONAR_TEST_PERFORM && ServicePerformSonar_ANS(mainJobName)}
                }  
                options {
                    timeout(time: 5, unit: 'MINUTES')
                    retry(2)
                }
                environment {
                        SCANNER_HOME = tool 'MySonarScanner'
                }
                steps {
                    script {
                        STAGE_NAME = "SonarQube analysis"
                
                        withSonarQubeEnv('SonarQubeServer') {
                            sh "${SCANNER_HOME}/bin/sonar-scanner"
                            //sh "mvn clean package sonar:sonar"  // This wont consider sonar-project.properties file
                            def props = readProperties  file: '.scannerwork/report-task.txt'
                            SonarDashboardURL =  props['dashboardUrl']
                        }
            
                        //echo "ANS_SONAR_TEST_PERFORM : " + Constants.ANS_SONAR_TEST_PERFORM
                        //echo "ANS_SONAR_FAILURE_MAIL_REQUIRE : " + Constants.ANS_SONAR_FAILURE_MAIL_REQUIRE
                       waitForQualityGate abortPipeline: true   // temparory we have commented this line otherwise this should be aborted and should not build service.
			    //waitForQualityGate abortPipeline: false

                    }
                }
        
                post {
                    failure {
                            script{
                                if (Constants.ANS_SONAR_FAILURE_MAIL_REQUIRE){
                                    mail bcc: '', body: '' + MailBody('sonarFailureMailBody', SonarDashboardURL), cc: Constants.ANS_EMAILTO_CC, charset: 'UTF-8', from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: "${Constants.PROJECT_NAME} - " + serverName + ' : ABORTED - Sonar Testing Fail', to: Constants.ANS_EMAILTO     // from email id not mandatory
                                }
                            }
                    }
                    success {
                        script{
                            SonarStatus = Constants.SonarStatus.Passed
                        }
                    }
                }                               
            }

            stage("Build Project")
            {
                steps{
			//sh "mvn -Dmaven.test.failure.ignore=true clean install"		
                    //sh "mvn -Dmaven.repo.local=/var/jenkins_home/" + "${env.BRANCH_NAME}".toUpperCase() + " clean package install"
			sh "mvn -Dmaven.repo.local=/var/lib/jenkins/repository/" + serverName + " clean package install"
			
                }
                post {
                    // If Maven was able to run the tests, even if some of the test
                    // failed, record the test results and archive the jar file.
                    success {
                        //junit '**/target/surefire-reports/TEST-*.xml'
                        //archiveArtifacts artifacts:'**/*.jar', fingerprint: true
			    echo "MAP TESTING ${env.JOB_NAME} : " + ServicePerformSonar_ANS(mainJobName)
			    echo "Inside Success but outside of script: " + attachedLog
			    
                        script{
                            BuildSuccessfully = true

                            attachedLog = false
				echo "Inside Success : " + attachedLog


                        }
                    }
                    always
                    {
                        script {
                            if(buildMailRequire){
                            //if ("${ANS_Build_Mail_Require}"=="Yes")
				    echo "Inside Always : " + attachedLog
                            emailext attachLog: attachedLog, body: '' + MailBody('buildMailBody','','',SonarStatus), from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: "${Constants.PROJECT_NAME} - " + serverName + " Build Status ${currentBuild.currentResult}: Project name -> ${env.JOB_NAME}", to: Constants.ANS_EMAILTO + ",cc:" + Constants.ANS_EMAILTO_CC  // emailext used to send attachment, simple mail not able to send attachment.
				    
                            }
			    if(buildTeamsNotificationRequire){
				office365ConnectorSend webhookUrl: Constants.ANS_TEAMS_NOTIFICATION_WEBHOOK,
				message: MailBody('buildMailBody','','',SonarStatus),
				status: "Build ${currentBuild.currentResult}",
				remarks: ""
			    }
                        }
                    }
                }
            }
            stage("Archive Artifacts")
            {
                when {
                    expression {BuildSuccessfully}
                } 	
                steps{
			script{
				if(projectType.toUpperCase()=="WithParent".toUpperCase())
				    {
					    additionFolderName="service-*/"
				    }
			}
			 archiveArtifacts artifacts:additionFolderName + 'target/service-*.jar, target/*.jar', fingerprint: true
                    	// archiveArtifacts artifacts:additionFolderName + 'target/service-*.jar', fingerprint: true
			//archiveArtifacts artifacts:'**/target/service-*.jar', fingerprint: true
                println(additionFolderName)
                }
                post{
                    failure{
                        script {
                            if(Constants.ANS_BUILD_ARCHIVE_FAILURE_MAIL_REQUIRE){
                                mail bcc: '', body: '' + MailBody('archiveFailurMailBody'), cc: Constants.ANS_EMAILTO_CC, charset: 'UTF-8', from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: "${Constants.PROJECT_NAME} - " + serverName + " Archiving Artifacts : Failed for  ${env.JOB_NAME} (Build Number : ${env.BUILD_NUMBER})", to: Constants.ANS_EMAILTO
                            }
                        }
                    }
                }

            }
            stage("Docker Depolyment on UAT")
            {
            steps{
		    script{
			   
			    sourcePath=JENKINS_HOME + "/jobs/" + mainJobName + "/branches/" + BRANCH_NAME + "/builds/" + BUILD_NUMBER + "/archive/" + additionFolderName + "target/*.jar"
			   
			    println(sourcePath)
		}
		 	    
		    echo "URL :  ${env.BUILD_URL}"
		    //sh "scp /var/lib/jenkins/jobs/" + mainJobName + "/branches/${env.BRANCH_NAME}/builds/${env.BUILD_NUMBER}/archive/service-*/target/*.jar " + deploymentServerDetails + ":" + jenkinsFolderPath    // This line is also working fine and syntex is correct

		    //sh "scp " + sourcePath + " " + deploymentServerDetails + ":" + jenkinsFolderPath 
           	   
		    
		    script{
			//sourceDockerfile = JENKINS_HOME + "/workspace/" + mainJobName + "_" + BRANCH_NAME +  "/" + mainJobName + "-Dockerfile" 
			sourceDockerfile = WORKSPACE +  "/" + mainJobName + "-Dockerfile"
			println(sourceDockerfile)
			
			sh "scp " + sourcePath + " " + deploymentServerDetails + ":" + servicePath1 + mainJobName  // Copy jar to /apps/services/common/servicename
                        sh "scp " + sourceDockerfile + " " + deploymentServerDetails + ":" + servicePath1 + mainJobName  // Copy Dockerfile to /apps/services/common/servicename
			
 			def remote = [:]
			remote.name = "${serverName}"
			remote.host = "${deploymentServerIP}"
			remote.allowAnyHosts = true
			
			withCredentials([sshUserPrivateKey(credentialsId: 'ssh-sit-server', keyFileVariable: 'identity', passphraseVariable: '', usernameVariable: 'ans.pipeline')]) {
                    	remote.user = 'ans.pipeline'
                    	remote.identityFile = identity
			
			sshCommand remote: remote, sudo : true, command: "sudo -S sh ${jenkinsFolderPath}/docker.sh ${mainJobName} ${servicePath1}"   // Run docker.sh script on Remote server

if (mainJobName == 'service-itr-msme-ans' || mainJobName == 'service-analyzer-retail-ans') {
	sshCommand remote: remote, sudo : true, command: "sudo -S sh ${jenkinsFolderPath}/${mainJobName}.sh"
}

				
			} 
 
                    }

 }
                post{
		 	failure{
                        script {
                             if(Constants.ANS_BUILD_ARCHIVE_FAILURE_MAIL_REQUIRE){
                                mail bcc: '', body: '' + MailBody('archiveFailurMailBody'), cc: Constants.ANS_EMAILTO_CC, charset: 'UTF-8', from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: "${Constants.PROJECT_NAME} - " + serverName + " : Moving JAR to server Failed for  ${env.JOB_NAME} (Build Number : ${env.BUILD_NUMBER})", to: Constants.ANS_EMAILTO
                             }
                        }
                    }
                    always
                    {
                        script{
                            if (deploymentMailRequire){
                                mail bcc: '', body: '' + MailBody('deploymentMailBody','',serverName), cc:  Constants.ANS_EMAILTO_CC, charset: 'UTF-8', from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: "${Constants.PROJECT_NAME} - " + serverName + " : Deploy and build docker images for " + mainJobName + " on " + serverName + " server is ${currentBuild.currentResult}" , to: Constants.ANS_EMAILTO
                            }
			    if(deploymentTeamsNotificationRequire){
				office365ConnectorSend webhookUrl: Constants.ANS_TEAMS_NOTIFICATION_WEBHOOK,
				message: MailBody('deploymentMailBody','',SonarStatus),
				status: "Deployment ${currentBuild.currentResult}",
				remarks: ""
			    }
                        }
                        
                        
                    }
                }
            }



	    stage ("K8S Deployment") {
                when {
                    expression {"${env.BRANCH_NAME}".toUpperCase() == Constants.ANS_K8S_PERFORM_BRANCH.toUpperCase() && Constants.ANS_K8S_TEST_PERFORM }
                }  
                
                steps {
                       withAWS(credentials: 'aws', region: 'ap-south-1') {
                  script {
	           sh ('aws eks update-kubeconfig --name JP-PRD-Cluster --region ap-south-1')
if (mainJobName == 'service-reports-ans' || mainJobName == 'service-loans-retail-ans' || mainJobName == 'service-loans-agri-ans' || mainJobName == 'service-loans-msme-ans' || mainJobName == 'service-dms-ans' || mainJobName == 'service-aadhar-ans') {
sh "kubectl apply -f ${mainJobName}.yml"
}
else if (mainJobName == 'service-analyzer-retail-ans'){
	sh "sh service-analyzer-retail-ans-k8s.sh service-analyzer-retail-ans 11506 100m 500Mi 1 3 analyzer/retail"
	sh "sh service-analyzer-retail-ans-k8s.sh service-analyzer-agri-ans 11604 100m 500Mi 1 3 analyzer/agri"
	sh "sh service-analyzer-retail-ans-k8s.sh service-analyzer-msme-ans 10506 100m 500Mi 1 3 analyzer/msme"
	sh "sh service-analyzer-retail-ans-k8s.sh service-analyzer-livelihood-ans 11704 100m 500Mi 1 3 analyzer/lhd"
}
else if (mainJobName == 'service-itr-msme-ans'){
	sh "sh service-itr-msme-ans-k8s.sh service-itr-msme-ans 10502 100m 500Mi 1 3 itr/msme"
	sh "sh service-itr-msme-ans-k8s.sh service-itr-agri-ans 11603 100m 500Mi 1 3 itr/agri"
	sh "sh service-itr-msme-ans-k8s.sh service-itr-retail-ans 11502 100m 600Mi 1 3 itr/retail"
	sh "sh service-itr-msme-ans-k8s.sh service-itr-livelihood-ans 11707 100m 600Mi 1 3 itr/lhd"
}			  
else{
		    sh 'rm -rf /k8s/service-k8s-ans'
sh 'git clone <reponame>'                   
		    sh "sed -i 's|\${mainJobName}|${mainJobName}|g' /k8s/service-k8s-ans/ans-k8s-deployment.yaml"
          	    sh "sed -i 's|\${port}|${port}|g' /k8s/service-k8s-ans/ans-k8s-deployment.yaml"
	            sh "sed -i 's|\${requestsCpu}|${requestsCpu}|g' /k8s/service-k8s-ans/ans-k8s-deployment.yaml"
		    sh "sed -i 's|\${requestsMemory}|${requestsMemory}|g' /k8s/service-k8s-ans/ans-k8s-deployment.yaml"
		    sh "sed -i 's|\${minReplicas}|${minReplicas}|g' /k8s/service-k8s-ans/ans-k8s-deployment.yaml"
		    sh "sed -i 's|\${maxReplicas}|${maxReplicas}|g' /k8s/service-k8s-ans/ans-k8s-deployment.yaml"
		    sh "sed -i 's|\${healthcheckPath}|${healthcheckPath}|g' /k8s/service-k8s-ans/ans-k8s-deployment.yaml"
                    sh "kubectl apply -f /k8s/service-k8s-ans/ans-k8s-deployment.yaml"
}
		}
                }
        }
        
                                          
            }
		
	    		
	    
		
        }
    }
}
