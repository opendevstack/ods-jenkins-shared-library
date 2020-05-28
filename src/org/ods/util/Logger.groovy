package org.ods.util

class Logger implements ILogger, Serializable {

    private final Object script
    private final boolean debugOn
    private final Map clockStore = [ : ]

    Logger(script, debug) {
        this.script = script
        this.debugOn = debug
    }

    void debug(String message) {
        if (debugOn) {
            script.echo message
        }
    }

    void info(String message) {
        script.echo message
    }

    void debugClocked(String component, String message = null) {
        if (!component) {
            throw IllegalArgumentException ("Component can't be null!")
        }
        debug(timedCall(component, message))
    }

    void infoClocked(String component, String message = null) {
        if (!component) {
            throw IllegalArgumentException ("Component can't be null!")
        }
        info(timedCall(component, message))
    }

    boolean getDebugMode () {
        debugOn
    }

    void startClocked(String component) {
        timedCall (component)
    }

    private def timedCall (String component, String message = null) {
        def dumpMessage
        def startTime = clockStore[component]
        if (!startTime) {
            clockStore << [component, System.currentTimeMillis()]
            return dumpMessage = "[${component} - started]: ${message}"
        } else {
            def timeDuration = System.currentTimeMillis() - startTime
            if (message) {
                return dumpMessage = "[${component} - stopped]: ${message} " +
                    "took ${timeDuration}ms"
            } else {
                return dumpMessage = "[${component} - stopped]: took ${timeDuration}ms"
            }
        }
    }

}
