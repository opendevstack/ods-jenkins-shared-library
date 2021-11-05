package org.ods.util

class Util {
    static def findAll(Collection c, Closure filter) {
        def results = []
    
        for (def i = 0; i < c.size(); i++) {
            if (filter(c[i])) {
                results.add(c[i])
            }
        }
        
        return results
    }

    static def collectEntries(Collection c, Closure key, Closure value) {
        def results = [:]
    
        for (def i = 0; i < c.size(); i++) {
            results[key(c[i])] = value(c[i])
        }
        
        return results
    }

    static def executeAndRetryOnNonSerializableException(def script, Closure retryCondition, Closure block, int retries = 5, int waitTime = 5) {
        final def maxRetries = retries

        while (retryCondition() && retries-- > 0) {
            try {
                block()
            } catch (java.io.NotSerializableException err){
                script.echo ("WARN: Hit Jenkins serialization issue; attempt #: ${maxRetries - retries}")
                script.sleep(waitTime)
            }
        }
    }
}
