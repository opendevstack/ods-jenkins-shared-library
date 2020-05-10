package org.ods.services

import org.ods.util.ILogger

class JenkinsService {

    private final def script
    private final ILogger logger
    private static final String XUNIT_SYSTEM_RESULT_DIR = 'build/test-results/test'

    JenkinsService(script, ILogger logger) {
        this.script = script
        this.logger = logger
    }

    def stashTestResults(String customXunitResultsDir, String stashNamePostFix = 'stash') {
        def contextresultMap = [:]
        customXunitResultsDir = customXunitResultsDir?.trim()?.length() > 0 ?
            customXunitResultsDir : XUNIT_SYSTEM_RESULT_DIR

        logger.info "Stashing testResults from location: '${customXunitResultsDir}'"
        script.sh(
            script: """
            mkdir -p ${XUNIT_SYSTEM_RESULT_DIR} ${customXunitResultsDir} &&
                cp -rf ${customXunitResultsDir}/* ${XUNIT_SYSTEM_RESULT_DIR} | true
            """,
            label: "Moving test results to system location: ${XUNIT_SYSTEM_RESULT_DIR}"
          )

        def foundTests = script.sh(
            script: "ls -la ${XUNIT_SYSTEM_RESULT_DIR}/*.xml | wc -l",
            returnStdout: true,
            label: "Counting test results in ${XUNIT_SYSTEM_RESULT_DIR}"
        ).trim()

        logger.debug "Found ${foundTests} tests in '${XUNIT_SYSTEM_RESULT_DIR}'"

        contextresultMap.testResultsFolder = XUNIT_SYSTEM_RESULT_DIR
        contextresultMap.testResults = foundTests

        if (foundTests.toInteger() > 0) {
            script.junit(
                testResults: "${XUNIT_SYSTEM_RESULT_DIR}/**/*.xml",
                allowEmptyResults: true
            )

            def testStashPath = "test-reports-junit-xml-${stashNamePostFix}"
            contextresultMap.xunitTestResultsStashPath = testStashPath
            script.stash(
                name: "${testStashPath}",
                includes: "${XUNIT_SYSTEM_RESULT_DIR}/**/*.xml",
                allowEmpty: true
            )
        } else {
            logger.info 'No xUnit results found!!'
        }

        return contextresultMap
    }

    String getCurrentBuildLogAsHtml () {
        StringWriter writer = new StringWriter()
        this.script.currentBuild.getRawBuild().getLogText().writeHtmlTo(0, writer)
        return writer.getBuffer().toString()
    }

    String getCurrentBuildLogAsText () {
        StringWriter writer = new StringWriter()
        this.script.currentBuild.getRawBuild().getLogText().writeLogTo(0, writer)
        return writer.getBuffer().toString()
    }
  
    boolean unstashFilesIntoPath(String name, String path, String type) {
        def result = true
  
        this.script.dir(path) {
            try {
                this.script.unstash(name)
            } catch (e) {
                this.script.echo("Could not find any files of type '${type}' to unstash for name '${name}'")
                result = false
            }
        }
        return result
    }

}
