package org.ods.util

import static org.junit.Assert.*
import org.junit.Test
import org.ods.util.Logger
import org.ods.util.ILogger
import vars.test_helper.PipelineSpockTestBase

class LoggerSpec extends PipelineSpockTestBase {

    def "verify special debug logger flags"() {
        given:
        def steps = Spy(util.PipelineSteps)
        ILogger logger = new Logger(steps, debug)

        when:
        def logLevel = logger.debugMode
        String ocpLogLevel = logger.ocDebugFlag
        String shellLogFlag = logger.shellScriptDebugFlag

        then:
        logLevel == debug
        shellLogFlag == expectedShell
        ocpLogLevel == expectedOCP

        where:
        debug  || expectedOCP    | expectedShell
        true   || '--loglevel=5' | ''
        false  || ''             | 'set +x'
    }

    def "verify basic logging functionality"() {
        given:
        def steps = Spy(util.PipelineSteps)
        ILogger logger = new Logger(steps, debug)

        when:
        String logMessage = logger.debug("testDebug")
        String infoMessage = logger.info("testInfo")
        String warnMessage = logger.warn("testWarn")
        String errorMessage = logger.error("testError")

        then:
        logMessage == expectedLogDebug
        infoMessage == expectedInfo
        warnMessage == expectedWarn
        errorMessage == expectedError

        where:
        debug  || expectedLogDebug        | expectedInfo | expectedWarn      | expectedError
        true   || 'DEBUG: testDebug'      | 'testInfo'   | 'WARN: testWarn'  | 'ERROR: testError'
        false  || ''                      | 'testInfo'   | 'WARN: testWarn'  | 'ERROR: testError'
    }

    def "verify clocked logging functionality"() {
        given:
        def steps = Spy(util.PipelineSteps)
        ILogger logger = new Logger(steps, true)

        when:
        String startDebugLogMessage = logger.debugClocked('testDebugComponent')
        String endDebugLogMessage = logger.debugClocked('testDebugComponent', 'hehehhehe')
        String noStartDebugLogMessage = logger.debugClocked('testComponent')

        then:
        startDebugLogMessage.startsWith('DEBUG: [testDebugComponent]')
        !startDebugLogMessage.contains('took')

        endDebugLogMessage.startsWith('DEBUG: [testDebugComponent]')
        endDebugLogMessage.contains('hehehhehe')
        endDebugLogMessage.contains('took')

        noStartDebugLogMessage.startsWith('DEBUG: [testComponent]')
        !noStartDebugLogMessage.contains('took')
    }
}
