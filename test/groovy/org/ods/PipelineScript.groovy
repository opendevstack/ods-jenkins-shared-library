package org.ods

// We're stubbing out a pipeline script.
class PipelineScript {

    def env = [
            'BUILD_ID'   : 15,
            'BRANCH_NAME': 'PR-10',
            'JOB_NAME': 'foo-cd/foo-cd-JOB-10',
            'BITBUCKET_HOST': 'bitbucket.example.com',
            "getEnvironment" : { [ : ] }
    ]

    def scm

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
}
