package util

import org.ods.util.IPipelineSteps

class PipelineSteps implements IPipelineSteps {

    private Map env = [:].withDefault { "default" }

    PipelineSteps() {
        env.WORKSPACE = System.getProperty("java.io.tmpdir")
    }

    void archiveArtifacts(String artifacts) {
    }

    void dir(String path, Closure block) {
        block()
    }

    void echo(String message) {
    }

    Map env() {
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

    void stash(String name) {
    }

    void unstash(String name) {
    }
}
