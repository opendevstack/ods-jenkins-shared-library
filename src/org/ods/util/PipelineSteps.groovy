package org.ods.util

class PipelineSteps implements IPipelineSteps, Serializable {

    private context

    PipelineSteps(def context) {
        this.context = context
    }

    void dir(String path, Closure block) {
        this.context.dir(path, block)
    }

    void echo(String message) {
        this.context.echo(message)
    }

    void stash(String name) {
        this.context.stash(name)
    }

    void unstash(String name) {
        this.context.unstash(name: name)
    }
}
