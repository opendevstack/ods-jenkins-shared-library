package util

import org.ods.util.ILogger

class LoggerStub implements ILogger, Serializable {

    private final Object script
    private final Map clockStore = [ : ]

    LoggerStub(script) {
        this.script = script
    }


    String debug(String message) {
        script.debug ("DEBUG: ${message}")
    }

    String info(String message) {
        script.info message
    }

    String warn(String message) {
        info ("WARN: ${message}")
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

    boolean getDebugMode () {
        true
    }

    @Override
    String getOcDebugFlag() {
        return null
    }

    @Override
    String getShellScriptDebugFlag() {
        return null
    }

    String startClocked(String component) {
        timedCall (component)
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

}
