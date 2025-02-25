package org.ods

// We're stubbing out a pipeline script.
class PipelineScript {

    def env = [
            'BUILD_ID'   : 15,
            'BRANCH_NAME': 'PR-10',
            'JOB_NAME': 'foo-cd/foo-cd-JOB-10',
            'BUILD_NUMBER': '1',
            'BUILD_TAG': 'foo',
            'NEXUS_HOST': 'https//nexus.example.com',
            'NEXUS_USERNAME': 'foo',
            'NEXUS_PASSWORD': 'bar',
            'OPENSHIFT_API_URL': 'https://api.openshift.example.com',
            'BITBUCKET_HOST': 'bitbucket.example.com',
            "getEnvironment" : { [ : ] }
    ]

    def scm = [ "userRemoteConfigs" : "none" ]

    def currentBuild = [:]

    def USERPASS = 'secret'

    def node(String label, Closure body) {
        body()
    }

    def stage(String label, Closure body) {
        body()
    }

    def wrap(def foo, Closure body) {
        body()
    }

    def withCredentials(def foo, Closure body) {
        body()
    }

    def usernamePassword(def foo) {
    }

    def sh(def foo) {
        return "test"
    }

    def emailextrecipients(def foo) {
    }

    def emailext(def foo) {
    }

    def checkout(def foo) {
    }

    def usernameColonPassword(def foo) {
    }

    def PipelineScript() {
    }

    def error(msg) {
        println msg
    }

    def echo(msg) {
        println msg
    }

    def fileExists (file) {
        return true
    }

    def zip (Map cfg) {

    }

    def parallel (Map executors) {
        if (executors) {
            executors.remove ('failFast')
            executors.each { key, block ->
                println key
                block ()
            }
        }
    }

    def archiveArtifacts(Map args) {

    }

    def stash (Map args) {

    }

    def readFile (Map args) {

    }

    def writeFile (Map args) {

    }

    def readJSON (Map args) {

    }

    def dir(def foo, Closure body) {
        body()
    }

    def withSonarQubeEnv(String conf, Closure closure) {
        closure()
    }

    def SONAR_HOST_URL = "https://sonarqube.example.com"

    def SONAR_AUTH_TOKEN = "Token"
}
