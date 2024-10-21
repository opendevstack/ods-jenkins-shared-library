package org.ods.core.test

import groovy.json.JsonOutput
import org.ods.util.ILogger

class LoggerStub implements ILogger, Serializable {

    private final Object script
    private final boolean debugOn = true
    private final Map clockStore = [ : ]
    private Object logger

    LoggerStub(logger) {
        this.logger = logger
    }

    String debug(String message) {
        logger.debug message
    }

    String jsonDebug(Object jsonObject, String message = null,  boolean pretty = true) {
        debug(jsonMessage(jsonObject, message, pretty))
    }

    String info(String message) {
        logger.info message
    }

    String jsonInfo(Object jsonObject, String message = null,  boolean pretty = true) {
        info(jsonMessage(jsonObject, message, pretty))
    }

    String warn(String message) {
        info ("WARN: ${message}")
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

    boolean getDebugMode () {
        debugOn
    }

    String getOcDebugFlag () {
        return "debugOn"
    }
    String getShellScriptDebugFlag () {
        return "debugOn"
    }

    @Override
    def dumpCurrentStopwatchSize() {
        return null
    }

    @Override
    def resetStopwatch() {
        return null
    }

    String startClocked(String component) {
        timedCall (component)
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
    private def timedCall (String component, String message = null) {
        if (!component) {
            throw new IllegalArgumentException ("Component can't be null!")
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

    @Override
    String error(String message) {
        info ("ERROR: ${message}")
    }

    @Override
    String errorClocked(String component, String message = null) {
        error(timedCall(component, message))
    }
}
