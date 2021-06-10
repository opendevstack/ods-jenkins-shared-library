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
            def duration = Duration.ofMillis(end - start)
            ILogger logger = ServiceRegistry.instance.get(Logger)
            logger.info("${blockName} duration: ${duration}")
        }
    }

}
