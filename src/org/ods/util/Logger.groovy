package org.ods.util


import groovy.json.JsonOutput

class Logger implements ILogger, Serializable {

    private final Object script
    private final boolean debugOn
    private final Map clockStore = [:]

    Logger(script, debug) {
        this.script = script
        this.debugOn = debug
    }

    String debug(String message) {
        if (debugOn) {
            message = "DEBUG: ${message}"
            info(message)
        } else {
            return ''
        }
    }

    String jsonDebug(def jsonObject, String message = null,  boolean pretty = true) {
        debug(jsonMessage(jsonObject, message, pretty))
    }

    String logWithThrow(String message) {
        this.script.echo("About to throw: ${message}")
        this.script.currentBuild.result = 'FAILURE'
        throw new IllegalStateException('bla bla bla')
    }

    String info(String message) {
        script.echo message
        message
    }

    String jsonInfo(Object jsonObject, String message = null,  boolean pretty = true) {
        info(jsonMessage(jsonObject, message, pretty))
    }

    String warn(String message) {
        message = "WARN: ${message}"
        info(message)
    }

    String debugClocked(String component, String message = null) {
        debug(timedCall(component, message))
    }

    String jsonDebugClocked(String component, Object jsonObject, String message = null, boolean pretty = true) {
        debug(timedCall(component, jsonMessage(jsonObject, message, pretty)))
    }

    String infoClocked(String component, String message = null) {
        info(timedCall(component, message))
    }

    String jsonInfoClocked(String component, Object jsonObject, String message = null, boolean pretty = true) {
        info(timedCall(component, jsonMessage(jsonObject, message, pretty)))
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

    private def toJson(Object jsonObject, boolean pretty = true) {
        def json = JsonOutput.toJson(jsonObject)
        json = pretty ? JsonOutput.prettyPrint(json) : json
        return json
    }

    private def jsonMessage(Object jsonObject, String message, boolean pretty) {
        def json = toJson(jsonObject, pretty)
        def prefix = message ? "${message}, json" : 'json'

        def msg = "${prefix}: ${json}"

        return msg
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
        debug("Stopwatch size: ${clockStore.size()}")
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
