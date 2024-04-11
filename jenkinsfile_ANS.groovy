import org.ans.Constants

def call(String projectType='WithParent') {
	
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

	switch(BRANCH_NAME.toUpperCase()){
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
			break;
	}
	

    pipeline {
        agent any
	//options {                 
	//	skipDefaultCheckout true   //Multibranch will automatically pull data from repository at start so if wants to ignore it and wants to do it manually then this line is useful.
	//    }
	//triggers { cron('H/5 * * * *') }
	    triggers {pollSCM('H/5 * * * *')}
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
                                    mail bcc: '', body: '' + MailBody('sonarFailureMailBody', SonarDashboardURL), cc: Constants.ANS_EMAILTO_CC, charset: 'UTF-8', from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: 'ABORTED : Sonar Testing Fail', to: Constants.ANS_EMAILTO     // from email id not mandatory
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
			// sh "mvn -Dmaven.test.failure.ignore=true clean install"		
                    //sh "mvn -Dmaven.repo.local=/var/lib/jenkins/repository/" + "${env.BRANCH_NAME}".toUpperCase() + " clean package install"
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
                            emailext attachLog: attachedLog, body: '' + MailBody('buildMailBody','','',SonarStatus), from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: "Build Status ${currentBuild.currentResult}: Project name -> ${env.JOB_NAME}", to: Constants.ANS_EMAILTO + ",cc:" + Constants.ANS_EMAILTO_CC  // emailext used to send attachment, simple mail not able to send attachment.
				    
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
                    //archiveArtifacts artifacts:additionFolderName + 'target/service-*.jar', fingerprint: true
			//archiveArtifacts artifacts:'**/target/service-*.jar', fingerprint: true
                
                }
                post{
                    failure{
                        script {
                            if(Constants.ANS_BUILD_ARCHIVE_FAILURE_MAIL_REQUIRE){
                                mail bcc: '', body: '' + MailBody('archiveFailurMailBody'), cc: Constants.ANS_EMAILTO_CC, charset: 'UTF-8', from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: "Archiving Artifacts : Failed for  ${env.JOB_NAME} (Build Number : ${env.BUILD_NUMBER})", to: Constants.ANS_EMAILTO
                            }
                        }
                    }
                }

            }
            stage("Depolyment on Server")
            {
            steps{
		    script{
			   
			    sourcePath=JENKINS_HOME + "/jobs/" + mainJobName + "/branches/" + BRANCH_NAME + "/builds/" + BUILD_NUMBER + "/archive/" + additionFolderName + "target/*.jar"
			   
			    println(sourcePath)
		    }
		    
		    echo "URL :  ${env.BUILD_URL}"
		    //sh "scp /var/lib/jenkins/jobs/" + mainJobName + "/branches/${env.BRANCH_NAME}/builds/${env.BUILD_NUMBER}/archive/service-*/target/*.jar " + deploymentServerDetails + ":" + jenkinsFolderPath    // This line is also working fine and syntex is correct
		    sh "scp " + sourcePath + " " + deploymentServerDetails + ":" + jenkinsFolderPath 
                    sh "ssh -tt " + deploymentServerDetails + " 'echo "+ deploymentServerPwd +" | sudo -S sh " + jenkinsFolderPath + "/" + deployFileName + " " + mainJobName + "' &"
                }
                post{
                    always
                    {
                        script{
                            if (deploymentMailRequire){
                                mail bcc: '', body: '' + MailBody('deploymentMailBody','',serverName), cc:  Constants.ANS_EMAILTO_CC, charset: 'UTF-8', from: 'jnk.pipeline@onlinepsbloans.com', mimeType: 'text/html', replyTo: '', subject: "${currentBuild.currentResult} : Deployment for " + mainJobName + " on " + serverName + " server is ${currentBuild.currentResult}" , to: Constants.ANS_EMAILTO
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
        }
    }
}
