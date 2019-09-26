package org.ods.util

class PipelineSteps implements IPipelineSteps, Serializable {

    private def context

    PipelineSteps(def context) {
        this.context = context
    }

    void archiveArtifacts(String artifacts) {
        this.context.archiveArtifacts(artifacts)
    }

    def checkout(Map config) {
        return this.context.checkout(config)
    }

    void dir(String path, Closure block) {
        this.context.dir(path, block)
    }

    void echo(String message) {
        this.context.echo(message)
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

    def load(String path) {
        return this.context.load(path)
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

    void unstash(String name) {
        this.context.unstash(name: name)
    }
}
