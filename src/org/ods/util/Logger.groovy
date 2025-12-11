package org.ods.util

import com.cloudbees.groovy.cps.NonCPS

class Logger implements ILogger, Serializable {

    private final Object script
    private final boolean debugOn
    private final Map clockStore = [:]

    Logger(script, debug) {
        this.script = script
        this.debugOn = debug
    }

    @NonCPS
    String debug(CharSequence message) {
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
        throw new IllegalStateException(message)
    }

    @NonCPS
    String info(String message) {
        script.echo message
        message
    }

    @NonCPS
    String warn(String message) {
        message = "WARN: ${message}"
        info(message)
    }

    @NonCPS
    String debugClocked(String component, CharSequence message = null) {
        debug(timedCall(component, message))
    }

    @NonCPS
    String infoClocked(String component, String message = null) {
        info(timedCall(component, message))
    }

    @NonCPS
    String warnClocked(String component, String message = null) {
        warn(timedCall(component, message))
    }

    @NonCPS
    boolean getDebugMode() {
        debugOn
    }

    @NonCPS
    String getOcDebugFlag() {
        return debugOn ? '--loglevel=5' : ''
    }

    @NonCPS
    String getShellScriptDebugFlag() {
        return debugOn ? '' : 'set +x'
    }

    @NonCPS
    String startClocked(String component) {
        timedCall(component)
    }

    @NonCPS
    @SuppressWarnings(['GStringAsMapKey', 'UnnecessaryElseStatement'])
    private def timedCall(String component, CharSequence message = null) {
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

    @NonCPS
    def dumpCurrentStopwatchSize() {
        debug("Stopwatch size: ${clockStore.size()}")
    }

    @NonCPS
    def resetStopwatch() {
        dumpCurrentStopwatchSize()
        clockStore.clear()
        dumpCurrentStopwatchSize()
    }

    @NonCPS
    @Override
    String error(String message) {
        message = "ERROR: ${message}"
        info(message)
    }

    @NonCPS
    @Override
    String errorClocked(String component, String message = null) {
        error(timedCall(component, message))
    }
}
