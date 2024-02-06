package org.ods.util

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')

import com.cloudbees.groovy.cps.NonCPS
import kong.unirest.Unirest

class UnirestConfig {

    @NonCPS
    static void init() {
        Unirest.config()
            .socketTimeout(6_000_000) // 6_000 sec, 100 min
            .connectTimeout(600_000) // 600 sec, 10 min
    }

    @NonCPS
    static void shutdown() {
        Unirest.shutDown()
    }

}
