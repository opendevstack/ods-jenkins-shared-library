package org.ods.util

import java.util.concurrent.ExecutionException

class ShellWithRetry {

    static final int MAX_RETRIES = 5
    static final int WAIT_TIME_SECONDS = 5

    private final ILogger logger
    private final def jenkinsFileContext

    ShellWithRetry(def jenkinsFileContext, ILogger logger) {
        this.jenkinsFileContext = jenkinsFileContext
        this.logger = logger
    }

    String execute(Map shellParams) {
        String returnScript
        int retry = 0
        boolean executedWithErrors = true
        while (executedWithErrors && retry++ < MAX_RETRIES) {
            try {
                returnScript = jenkinsFileContext.sh(
                    script: shellParams.script,
                    returnStdout: shellParams.returnStdout,
                    label: shellParams.label
                )
                executedWithErrors = false
            } catch (java.io.NotSerializableException err) {
                logger.warn ("WARN: Jenkins serialization issue; attempt #: ${retry}, when: [${shellParams.script}]")
                jenkinsFileContext.sleep(WAIT_TIME_SECONDS)
            }
        }

        if (executedWithErrors) {
            throw new ExecutionException("Jenkins serialization issue, when: [${shellParams.script}]")
        }
        return returnScript ? returnScript.trim() : ""
    }

}
