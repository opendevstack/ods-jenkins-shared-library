package util

import org.ods.util.IPipelineSteps

class PipelineSteps implements IPipelineSteps {

    private Map env = [:].withDefault { "default" }

    PipelineSteps() {
        env.WORKSPACE = System.getProperty("java.io.tmpdir")
    }

    void archiveArtifacts(String artifacts) {
    }

    def checkout(Map config) {
        return [:]
    }

    void dir(String path, Closure block) {
        block()
    }

    void echo(String message) {
    }

    def getCurrentBuild() {
        return [:]
    }

    Map getEnv() {
        return env
    }

    void junit(String path) {
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

    void unstash(String name) {
    }
}
