package org.ods.util

interface ILogger {

    void info(String message)
    void debug(String message)

    void startClocked(String component)
    void debugClocked(String component, String message)
    void infoClocked(String component, String message)

    void warn(String message)

    boolean getDebugMode ()

}
