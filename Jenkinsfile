pipeline {
    agent {
        kubernetes {
            label 'mosaic-ci-pod'
            yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: maven-sumo
    image: eclipsemosaic/mosaic-ci:jdk17-sumo-1.23.0
    command:
    - cat
    tty: true
    volumeMounts:
    - mountPath: "/home/jenkins/.m2/settings-security.xml"
      name: "settings-security-xml"
      readOnly: true
      subPath: "settings-security.xml"
    - mountPath: "/home/jenkins/.m2/settings.xml"
      name: "settings-xml"
      readOnly: true
      subPath: "settings.xml"
    - name: m2-repo
      mountPath: /home/jenkins/.m2/repository
    - mountPath: "/opt/tools"
      name: "volume-0"
      readOnly: false
    resources:
      limits:
        memory: "2Gi"
        cpu: "1"
      requests:
        memory: "2Gi"
        cpu: "1"    
  volumes:
  - name: "settings-security-xml"
    secret:
      items:
      - key: "settings-security.xml"
        path: "settings-security.xml"
      secretName: "m2-secret-dir"
  - name: "settings-xml"
    secret:
      items:
      - key: "settings.xml"
        path: "settings.xml"
      secretName: "m2-secret-dir"      
  - name: m2-repo
    emptyDir: {}
  - name: "volume-0"
    persistentVolumeClaim:
      claimName: "tools-claim-jiro-mosaic"
      readOnly: true
"""
        }
    }

    options {
        buildDiscarder(logRotator(numToKeepStr: '10'))
    }

    stages {
        stage('Build') {
            steps {
                container('maven-sumo') {
                    sh 'mvn clean install -DskipTests -fae -T 4'
                }
            }
        }

        stage('Test') {
            steps {
                container('maven-sumo') {
                    sh 'mvn test -fae -T 4 -P coverage'
                }
            }

            post {
                always {
                    junit '**/surefire-reports/*.xml'
                }
            }
        }

        stage('Integration Tests') {
            steps {
                container('maven-sumo') {
                    sh 'mvn test -fae -P integration-tests,coverage'
                }
            }

            post {
                always {
                    junit 'test/**/surefire-reports/*.xml'
                }
            }
        }

        stage('Analysis') {
            steps {
                container('maven-sumo') {
                    sh 'mvn site'
                }
            }

            post {
                always {
                    jacoco exclusionPattern: '**/ClientServerChannelProtos*.class', skipCopyOfSrcFiles: true, sourceExclusionPattern: '**/*.*', sourceInclusionPattern: '', sourcePattern: 'x'
                    recordIssues(sourceCodeEncoding: 'UTF-8', tools: [
                            spotBugs(),
                            checkStyle(),
                            taskScanner(highTags: 'FIXME', normalTags: 'TODO', ignoreCase: true, includePattern: '**/*.java')
                    ])
                }
            }
        }


        stage ('Deploy') {
            when {
                branch 'main'
            }
            steps {
                container('maven-sumo') {
                    // sometimes this fails for unknown reason, so lets try it three times
                    retry(3) {
                        // requires the correct tool finding secrets
                        sh '/opt/tools/apache-maven/3.6.3/bin/mvn deploy -DskipTests'
                    }
                }
            }
            post {
                success {
                    archiveArtifacts artifacts: 'bundle/target/eclipse-mosaic-*.*', caseSensitive: false, onlyIfSuccessful: true
                }
            }
        }
    }

    post {
        success {
            cleanWs()
        }
        fixed {
            sendMail("Successful")
        }
        unstable {
            sendMail("Unstable")
        }
        failure {
            sendMail("Failed")
        }
    }
}

def sendMail(status) {
    emailext(
            recipientProviders: [culprits()],
            subject: status + ": ${currentBuild.fullDisplayName}",
            body: '${JELLY_SCRIPT, template="html-with-health-and-console"}',
            mimeType: 'text/html'
    )
}