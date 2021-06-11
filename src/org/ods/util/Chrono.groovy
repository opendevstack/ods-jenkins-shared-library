package org.ods.util


import org.ods.services.ServiceRegistry

import java.time.Duration

class Chrono {

    static <T> T time(String blockName, Closure<T> body) {
        def start = System.currentTimeMillis()
        try {
            return body()
        } finally {
            def end = System.currentTimeMillis()
            ILogger logger = ServiceRegistry.instance.get(Logger)
            if(logger) {
                def duration = Duration.ofMillis(end - start)
                logger.info("${blockName} duration: ${duration}")
            }
        }
    }

}
