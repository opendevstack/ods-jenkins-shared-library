package org.ods.util

class PipelineSteps implements IPipelineSteps, Serializable {

    private def context

    PipelineSteps(def context) {
        this.context = context
    }

    void archiveArtifacts(String artifacts) {
        this.context.archiveArtifacts(artifacts)
    }

    void dir(String path, Closure block) {
        this.context.dir(path, block)
    }

    void echo(String message) {
        this.context.echo(message)
    }

    Map env() {
        return this.context.env
    }

    void junit(String path) {
        this.context.junit(path)
    }

    def load(String path) {
        return this.context.load(path)
    }

    def sh(def args) {
        this.context.sh(args)
    }

    void stash(String name) {
        this.context.stash(name)
    }

    void unstash(String name) {
        this.context.unstash(name: name)
    }
}
