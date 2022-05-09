package org.ods.util

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')

import com.cloudbees.groovy.cps.NonCPS
import kong.unirest.Unirest

class UnirestConfig {

    @NonCPS
    static void init() {
        Unirest.config().socketTimeout(6000000).connectTimeout(600000)
    }

    @NonCPS
    static void shutdown() {
        Unirest.shutDown()
    }

}
