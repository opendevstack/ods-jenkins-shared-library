package util

import groovy.util.logging.Slf4j
import org.ods.util.IPipelineSteps

import groovy.json.JsonSlurper
import org.yaml.snakeyaml.Yaml

@Slf4j
class PipelineSteps implements IPipelineSteps {

    def SONAR_HOST_URL = "https://sonarqube.example.com"

    def SONAR_AUTH_TOKEN = "Token"

    def env = [
            'BUILD_ID'   : 15,
            'BRANCH_NAME': 'PR-10',
            'JOB_NAME': 'foo-cd/foo-cd-JOB-10',
            'BUILD_NUMBER': '1',
            'BUILD_TAG': 'foo',
            'BUILD_URL': 'https://jenkins.com/job/foo-cd/job/foo-cd-JOB/15',
            'NEXUS_HOST': 'https//nexus.example.com',
            'NEXUS_USERNAME': 'foo',
            'NEXUS_PASSWORD': 'bar',
            'OPENSHIFT_API_URL': 'https://api.openshift.example.com',
            'BITBUCKET_HOST': 'bitbucket.example.com',
            "getEnvironment" : { [ : ] }
    ]

    def scm = [ "userRemoteConfigs" : "none" ]

    def currentBuild = [
        "getPreviousBuild" : { [ ] },
    ]

    def containerTemplate(Map template) {
        return template
    }

    def podTemplate(Map template, Closure body = null) {
        if (body) {
            body()
        }
    }

    PipelineSteps() {
        env.WORKSPACE = System.getProperty("java.io.tmpdir")
    }

    String libraryResource(String path) {
        return ""
    }

    void archiveArtifacts(String artifacts) {
    }

    void archiveArtifacts(Map args) {
    }

    def checkout(Map config) {
        return [:]
    }

    void dir(String path, Closure block) {
        block()
    }

    void error(String message) {
        println message
    }

    void echo(String message) {
        println message
    }

    def getCurrentBuild() {
        return currentBuild
    }

    Map getEnv() {
        return env
    }

    void junit(String path) {
    }

    void junit(Map config) {
    }

    def load(String path) {
        return [:]
    }

    def sh(def args) {
        return ""
    }

    void stage(String name, Closure block) {
        block()
    }

    void stash(String name) {
    }

    void stash(Map config) {
    }

    void unstash(String name) {
    }

    @Override
    def fileExists(String file) {
        return null
    }

    @Override
    def readFile(String file, String encoding) {
        return null
    }

    def readFile(Map args) {
        log.debug("readFile file: ${env.WORKSPACE}/${args.file}")
        try {
            return new File("${env.WORKSPACE}/${args.file}")?.text
        } catch(Exception exception){
            return ""
        }
    }

    @Override
    def writeFile(String file, String text, String encoding) {
        return null
    }

    @Override
    def writeFile(Map args) {
        return null
    }

    @Override
    def readJSON(Map args) {
        log.debug("readJSON file: ${args.text}")
        new JsonSlurper().parseText(args.text)
    }

    @Override
    def readYaml(Map args) {
        new Yaml().load(args.text)
    }

    def readYaml(Map args, Closure body) {
        def metadata = [:]
        if(args.file) {
            def fromFile = body(args.file)
            if (fromFile == null) {
                throw new FileNotFoundException(args.file, 'The requested file could not be found')
            }
            metadata.putAll(fromFile)
        }
        if(args.text) {
            def fromText = new Yaml().load(args.text)
            metadata.putAll(fromText)
        }
        return metadata
    }

    @Override
    def writeYaml(Map args) {
        return null
    }

    @Override
    def writeJSON(Map args) {
        return null
    }

    @Override
    def timeout(Map args, Closure block) {
        return null
    }

    @Override
    def deleteDir() {
        return null
    }

    def sleep(int seconds) {
        return null
    }

    @Override
    def withEnv(java.util.List env, groovy.lang.Closure block) {
      block()
    }

    @Override
    def unstable(String message) {
    }

    def usernamePassword(Map credentialsData) {
    }

    def sshUserPrivateKey(Map credentialsData) {
    }

    def string(Map credentialsData) {
    }

    def withCredentials(List credentialsList, Closure block) {
      block()
    }

    def get(def key) {
      return currentBuild.get(key)
    }

    def put(def key, def value) {
      currentBuild.put(key, value)
    }

    def unwrap() {
      return [:]
    }

    def wrap(Map, Closure block) {
      block ()
    }

    def node (String name, Closure block) {
      block ()
    }

    def emailext(Map args) {

    }

    def findFiles (Map args) {
        return []
    }

    def podAnnotation (Map arg) {
        return arg
    }

    def withSonarQubeEnv(String conf, Closure closure) {
        closure()
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

}
