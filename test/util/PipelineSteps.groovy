package util

import org.ods.util.IPipelineSteps

class PipelineSteps implements IPipelineSteps {

    private Map env = [:].withDefault { "default" }

    void dir(String path, Closure block) {
        block()
    }

    void echo(String message) {
    }

    Map env() {
        return env
    }

    void stash(String name) {
    }

    void unstash(String name) {
    }
}
