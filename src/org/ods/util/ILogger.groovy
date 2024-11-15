package org.ods.util

interface ILogger {

    String startClocked(String component)

    String info(String message)
    String infoClocked(String component, String message)

    String debug(String message)
    String debugClocked(String component, String message)

    String warn(String message)
    String warnClocked(String component, String message)

    String error(String message)
    String errorClocked(String component, String message)

    boolean getDebugMode ()

    String getOcDebugFlag ()
    String getShellScriptDebugFlag ()

    def dumpCurrentStopwatchSize()
    def resetStopwatch()

}
