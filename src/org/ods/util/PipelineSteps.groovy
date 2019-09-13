package org.ods.util

class PipelineSteps implements IPipelineSteps, Serializable {

    private def context
    private Map env = [:].withDefault { "default" }

    PipelineSteps(def context) {
        this.context = context
    }

    void dir(String path, Closure block) {
        this.context.dir(path, block)
    }

    void echo(String message) {
        this.context.echo(message)
    }

    Map env() {
        return env
    }

    void stash(String name) {
        this.context.stash(name)
    }

    void unstash(String name) {
        this.context.unstash(name: name)
    }
}
