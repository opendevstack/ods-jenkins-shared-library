package org.ods.util

interface ILogger {

    String startClocked(String component)

    String info(String message)
    String infoClocked(String component, String message)

    String jsonInfo(Object jsonObject, String message,  boolean pretty)
    String jsonInfoClocked(String component, Object jsonObject, String message, boolean pretty)

    String debug(String message)
    String debugClocked(String component, String message)

    String jsonDebug(Object jsonObject, String message,  boolean pretty)
    String jsonDebugClocked(String component, Object jsonObject, String message, boolean pretty)

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
