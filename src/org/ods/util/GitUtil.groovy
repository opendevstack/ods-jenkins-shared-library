package org.ods.util

import org.ods.util.IPipelineSteps

class GitUtil {

    private IPipelineSteps steps

    GitUtil(IPipelineSteps steps) {
        this.steps = steps
    }

    private String execute(String cmd) {
        return this.steps.sh(returnStdout: true, script: cmd).trim()
    }

    String getCommit() {
        return this.execute("git rev-parse HEAD")
    }

    String getURL() {
        return this.execute("git config --get remote.origin.url")
    }
}
