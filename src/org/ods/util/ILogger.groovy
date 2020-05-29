package org.ods.util

interface ILogger {

    void info(String message)
    void infoClocked(String component, String message)
    
    void debug(String message)
    void debugClocked(String component, String message)

    void warn(String message)
    void warnClocked(String component, String message)

    void startClocked(String component)

    boolean getDebugMode ()

}
