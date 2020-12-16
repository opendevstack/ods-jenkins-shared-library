package org.ods.services

import org.ods.util.ILogger

class JenkinsService {

    private static final String XUNIT_SYSTEM_RESULT_DIR = 'build/test-results/test'

    private final def script
    private final ILogger logger

    JenkinsService(script, ILogger logger) {
        this.script = script
        this.logger = logger
    }

    def stashTestResults(String customXunitResultsDir, String stashNamePostFix = 'stash') {
        def contextresultMap = [:]
        def xUnitResultDir = XUNIT_SYSTEM_RESULT_DIR
        if (customXunitResultsDir?.trim()?.length() > 0) {
            logger.debug "overwritten testresult location: ${customXunitResultsDir}"
            xUnitResultDir = customXunitResultsDir.trim()
        }

        logger.debug "Collecting (applicable) testresults from location: '${xUnitResultDir}'"
        script.sh(
            script: """
                ${logger.shellScriptDebugFlag}
                mkdir -p ${XUNIT_SYSTEM_RESULT_DIR} ${xUnitResultDir} &&
                cp -rf ${xUnitResultDir}/* ${XUNIT_SYSTEM_RESULT_DIR} | true
            """,
            label: "Moving test results to system location: ${XUNIT_SYSTEM_RESULT_DIR}"
          )

        def foundTests = script.sh(
            script: """
                ${logger.shellScriptDebugFlag}
                ls -la ${XUNIT_SYSTEM_RESULT_DIR}/*.xml | wc -l
            """,
            returnStdout: true,
            label: "Counting test results in ${XUNIT_SYSTEM_RESULT_DIR}"
        ).trim()

        logger.debug "Found ${foundTests} test files in '${XUNIT_SYSTEM_RESULT_DIR}'"

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
            logger.debug 'No xUnit results for stashing'
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
                logger.info ("Could not find any files of type '${type}' to unstash for name '${name}'")
                result = false
            }
        }
        return result
    }

    def maybeWithPrivateKeyCredentials(String credentialsId, Closure block) {
        if (privateKeyExists(credentialsId)) {
            script.withCredentials([
                script.sshUserPrivateKey(
                    credentialsId: credentialsId,
                    keyFileVariable: 'PKEY_FILE'
                )
            ]) {
                block(script.env.PKEY_FILE)
            }
        } else {
            block('')
        }
    }

    boolean privateKeyExists(String privateKeyCredentialsId) {
        try {
            script.withCredentials(
                [script.sshUserPrivateKey(credentialsId: privateKeyCredentialsId, keyFileVariable: 'PKEY_FILE')]
            ) {
                true
            }
        } catch (_) {
            false
        }
    }

}
