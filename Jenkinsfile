properties([pipelineTriggers([githubPush()])])

pipeline {
  options {
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '3'))
    timeout(time: 30, unit: 'MINUTES')
  }

  agent {
    kubernetes {
      label 'worker-sbt-swagger-models'
      inheritFrom 'default'
      containerTemplates([
        containerTemplate(
            name: 'sbt',
            image: 'gcr.io/aylien-production/sbt-jdk11',
            alwaysPullImage: true,
            privileged: true,
            ttyEnabled: true
        )
      ])
    }
  }

  stages {
    stage('Checkout') {
      steps {
        checkoutWithTags scm
      }
    }

    stage('Test') {
      when { allOf { not { branch 'master' }; expression { return env.CHANGE_ID != null } } }
      steps {
        container('sbt') {
          withCredentials([string(credentialsId: "jenkins-hub-api-token", variable: "GITHUB_TOKEN")]) {
            script {
              sh('SBT_OPTS="-Xmx1G" sbt clean test')
            }
          }
        }
        script {
          currentBuild.result = 'SUCCESS'
        }
        step([$class: 'CompareCoverageAction', publishResultAs: 'statusCheck', scmVars: [GIT_URL: env.GIT_URL]])
      }
    }

    stage('Publish') {
      when { branch 'master' }
      steps {
        container('sbt') {
          withCredentials([string(credentialsId: "jenkins-hub-api-token", variable: "GITHUB_TOKEN")]) {
            script {
              sh('sbt clean publish')
            }
          }
        }
      }
    }
  }
}
