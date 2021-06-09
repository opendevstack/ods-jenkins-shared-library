package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import org.ods.services.ServiceRegistry

import java.time.Duration

class Chrono {

    static void time(String blockName, Closure body) {
        def start = System.currentTimeMillis()
        try {
            body()
        } finally {
            def end = System.currentTimeMillis()
            def duration = Duration.ofMillis(end - start)
            ILogger logger = ServiceRegistry.instance.get(Logger)
            logger.info("${blockName} duration: ${duration}")
        }
    }

}
