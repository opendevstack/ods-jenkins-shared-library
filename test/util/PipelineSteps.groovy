package util

import org.ods.util.IPipelineSteps

class PipelineSteps implements IPipelineSteps {

    void dir(String path, Closure block) {
        block()
    }

    void echo(String message) {
    }

    void stash(String name) {
    }

    void unstash(String name) {
    }
}
