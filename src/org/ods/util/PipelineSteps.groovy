package org.ods.util

import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@SuppressWarnings('MethodCount')
class PipelineSteps implements IPipelineSteps, Serializable {

    private final def context

    PipelineSteps(def context) {
        this.context = context
    }

    void archiveArtifacts(String artifacts) {
        this.context.archiveArtifacts(artifacts)
    }

    void archiveArtifacts(Map args) {
        this.context.archiveArtifacts(args)
    }

    def checkout(Map config) {
        return this.context.checkout(config)
    }

    void dir(String path, Closure block) {
        this.context.dir(path, block)
    }

    void error(String message) {
        this.context.error(message)
    }

    void echo(String message) {
        def dateTimeString = OffsetDateTime
            .now(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss'Z'"))
        this.context.echo("${dateTimeString} ${message}")
    }

    def findFiles(Map config) {
        return this.context.findFiles(config)
    }

    def getCurrentBuild() {
        return this.context.currentBuild
    }

    Map getEnv() {
        return this.context.env
    }

    void junit(String path) {
        this.context.junit(path)
    }

    void junit(Map config) {
        this.context.junit(config)
    }

    def load(String path) {
        return this.context.load(path)
    }

    def node(String name, Closure block) {
        this.context.node (name, block)
    }

    def sh(def args) {
        this.context.sh(args)
    }

    void stage(String name, Closure block) {
        this.context.stage(name, block)
    }

    void stash(String name) {
        this.context.stash(name)
    }

    void stash(Map config) {
        this.context.stash(config)
    }

    void unstash(String name) {
        this.context.unstash(name: name)
    }

    def fileExists(String file) {
        this.context.fileExists(file)
    }

    def readFile(String file, String encoding = '') {
        this.context.readFile(file: file, encoding: encoding)
    }

    def readFile(Map args) {
        this.context.readFile(args)
    }

    def writeFile(String file, String text, String encoding = '') {
        this.context.writeFile(file: file, text: text, encoding: encoding)
    }

    def writeFile(Map args) {
        this.context.writeFile(args)
    }

    def readJSON(Map args) {
        this.context.readJSON(args)
    }

    def writeJSON(Map args) {
        this.context.writeJSON(args)
    }

    def readYaml(Map args) {
        this.context.readYaml(args)
    }

    def writeYaml(Map args) {
        this.context.writeYaml(args)
    }

    def timeout(Map args, Closure block) {
        this.context.timeout(args, block)
    }

    def deleteDir() {
        this.context.deleteDir()
    }

    def sleep(int seconds) {
        this.context.sleep(seconds)
    }

    def withEnv(List<String> env, Closure block) {
        this.context.withEnv (env, block)
    }

    def unstable(String message) {
        this.context.unstable(message)
    }

    def usernamePassword(Map credentialsData) {
        this.context.usernamePassword(credentialsData)
    }

    def sshUserPrivateKey(Map credentialsData) {
        this.context.sshUserPrivateKey(credentialsData)
    }

    def string(Map credentialsData) {
        this.context.string(credentialsData)
    }

    def withCredentials(List credentialsList, Closure block) {
        this.context.withCredentials(credentialsList, block)
    }

    def unwrap() {
        return this.context
    }

    def emailext(Map args) {
        this.context.emailext(args)
    }

}
