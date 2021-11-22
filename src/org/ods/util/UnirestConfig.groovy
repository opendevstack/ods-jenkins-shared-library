package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import kong.unirest.Unirest

class UnirestConfig {

    @NonCPS
    static void init() {
        Unirest.config().socketTimeout(6000000).connectTimeout(600000)
    }

}
