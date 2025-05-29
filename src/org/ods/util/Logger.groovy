package org.ods.util

class Logger implements ILogger, Serializable {

    private final Object script
    private final boolean debugOn
    private final Map clockStore = [:]
    private logFile

    Logger(script, debug) {
        this.script = script
        this.debugOn = debug
        // this.logFile = "${script.env.WORKSPACE}/pipeline-execution.log"
        // this.logFile = "${script.env.JENKINS_HOME}/jobs/${script.env.JOB_BASE_NAME}/workspace/pipeline-execution.log"
        this.logFile = "pipeline-execution2.log"
    }

    String debug(String message) {
        if (debugOn) {
            message = "DEBUG: ${message}"
            info(message)
        } else {
            return ''
        }
    }

    String logWithThrow(String message) {
        this.script.echo("About to throw: ${message}")
        this.script.currentBuild.result = 'FAILURE'
        throw new IllegalStateException('bla bla bla')
    }

    private void writeFile(String message) {
        try {
            try {
                script.unstash "pipeline-log-${script.env.BUILD_NUMBER}"
            } catch (Exception ignored) {
                // nothing to do here, the file might not exist yet
            }

            script.echo "DEBUG: AMP: inNode! Writing to log file '${logFile}'"
            script.writeFile file: logFile, text: message, append: true

            script.stash name: "pipeline-log-${script.env.BUILD_NUMBER}", includes: logFile
        } catch (Exception e) {
            script.echo "WARN: Unable to write to log file '${logFile}': ${e.message}"
        }
    }

    String info(String message) {
        script.echo message
        writeFile(message)
        message
    }

    String warn(String message) {
        message = "WARN: ${message}"
        writeFile(message)
        info(message)
    }

    String debugClocked(String component, String message = null) {
        debug(timedCall(component, message))
    }

    String infoClocked(String component, String message = null) {
        info(timedCall(component, message))
    }

    String warnClocked(String component, String message = null) {
        warn(timedCall(component, message))
    }

    boolean getDebugMode() {
        debugOn
    }

    String getOcDebugFlag() {
        return debugOn ? '--loglevel=5' : ''
    }

    String getShellScriptDebugFlag() {
        return debugOn ? '' : 'set +x'
    }

    String startClocked(String component) {
        timedCall(component)
    }

    @SuppressWarnings(['GStringAsMapKey', 'UnnecessaryElseStatement'])
    private def timedCall(String component, String message = null) {
        if (!component) {
            throw new IllegalArgumentException("Component can't be null!")
        }
        def startTime = clockStore.get("${component}")
        if (startTime) {
            def timeDuration = System.currentTimeMillis() - startTime
            return "[${component}] ${message ?: ''} " +
                "(took ${timeDuration} ms)"
        } else {
            clockStore << ["${component}": System.currentTimeMillis()]
            return "[${component}] ${message ?: ''}"
        }
    }

    def dumpCurrentStopwatchSize() {
        debug("Stopwatch size: ${clockStore.size()} AMP: Logger initialized with debugOn=${debugOn} and logFile=${logFile} ${script.env} ${script}")
        for (def envProp : script.env.getEnvironment().entrySet()) {
            debug("AMP: ${envProp.key} = ${envProp.value}")
        }
    }

    def resetStopwatch() {
        dumpCurrentStopwatchSize()
        clockStore.clear()
        dumpCurrentStopwatchSize()
    }

    @Override
    String error(String message) {
        message = "ERROR: ${message}"
        info(message)
    }

    @Override
    String errorClocked(String component, String message = null) {
        error(timedCall(component, message))
    }
}
