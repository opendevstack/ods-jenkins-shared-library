package org.ods.util

import org.ods.PipelineScript
import spock.lang.Specification
import spock.lang.Unroll

class ShellWithRetrySpec extends Specification {

    @Unroll
    def "execute with shell result:#resultShell"() {
        given:
        Map params = [ returnStdout: true,
                       script: "env | grep || true",
                       label: 'getting extension labels from current environment']
        PipelineScript script = Mock()
        ILogger logger = Mock()

        when:
        def shellReturn = new ShellWithRetry(script, logger).execute(params)

        then:
        1 * script.sh( params ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * script.sh( params ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * script.sh( params ) >> { return resultShell}
        shellReturn == resultMethod

        where:
        resultShell |   resultMethod
        "ok"        |   "ok"
        null        |   ""
    }

    def "execute with exception thrown"() {
        given:
        Map params = [ returnStdout: true,
                       script: "env | grep || true",
                       label: 'getting extension labels from current environment']
        PipelineScript script = Mock()
        ILogger logger = Mock()

        when:
        new ShellWithRetry(script, logger).execute(params)

        then:
        1 * script.sh( params ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * script.sh( params ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * script.sh( params ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * script.sh( params ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        1 * script.sh( params ) >> { throw new NotSerializableException("error")}
        1 * logger.warn{String it -> it.contains("WARN: Jenkins serialization issue")}

        thrown java.util.concurrent.ExecutionException
    }

}
