package org.ods.component

class Logger implements ILogger {

    private final Object script
    private final boolean debugOn

    Logger(script, debug) {
        this.debugOn = debugOn
        this.script = script
    }

    void debug(String message) {
        if (debugOn) {
            script.echo message
        }
    }

    void info(String message) {
        script.echo message
    }

}
