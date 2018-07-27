package org.ods

import groovy.mock.interceptor.*

class OdsPipelineTest extends GroovyTestCase {

    // We're stubbing out a pipeline script.
    class PipelineScript {

        def env = [
            'BUILD_ID': 15,
            'BRANCH_NAME': 'PR-10'
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

    }

    //
    // Tests
    //
    void testDetermineEnvironment() {

        def config = [gitBranch: 'master', projectId: 'PSP', autoCreateEnvironment: 'true']
        def pipeline = new PipelineScript()
        def uut = new OdsPipeline(pipeline, config)

        // DevTest Mode
        config.autoCreateEnvironment = false
        assertEquals("test", uut.determineEnvironment(config))

        config.gitBranch = "develop"
        assertEquals("dev", uut.determineEnvironment(config))

        config.gitBranch = "PSP-111"
        assertEquals("dev", uut.determineEnvironment(config))

        // Multi Environments Mode
        config.gitBranch = "master"
        config.autoCreateEnvironment = true
        assertEquals("test", uut.determineEnvironment(config))

        config.gitBranch = "develop"
        assertEquals("dev", uut.determineEnvironment(config))

        config.gitBranch = "master"
        config.projectId = "psp"
        config.gitBranch = "feature/" + config.projectId.toUpperCase() + "-ABC"
        assertEquals("branch name needs to be a number or lower case", "abc" + "-dev", uut.determineEnvironment(config))

        config.gitBranch ="something-else"
        assertEquals("", uut.determineEnvironment(config))

        config.gitBranch ="something_without_a_minus"
        assertEquals("", uut.determineEnvironment(config))

    }

}
