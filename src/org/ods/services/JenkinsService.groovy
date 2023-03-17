package org.ods.services

import org.ods.util.ILogger
import hudson.scm.ChangeLogSet

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
        logger.info ('Collecting test results, if available ...')
        def xUnitResultDir = XUNIT_SYSTEM_RESULT_DIR
        if (customXunitResultsDir?.trim()?.length() > 0) {
            logger.debug "Overwritten testresult location: ${customXunitResultsDir}"
            xUnitResultDir = customXunitResultsDir.trim()
            if (!script.fileExists(xUnitResultDir)) {
                throw new IOException ("Cannot use custom test directory '${xUnitResultDir}' that does not exist!")
            }
        }

        if (!script.fileExists(XUNIT_SYSTEM_RESULT_DIR)) {
            script.sh(script: "mkdir -p ${XUNIT_SYSTEM_RESULT_DIR}", label: "creating test directory")
        }

        if (XUNIT_SYSTEM_RESULT_DIR != xUnitResultDir) {
            logger.debug "Copying (applicable) testresults from location: '${xUnitResultDir}' to " +
                " '${XUNIT_SYSTEM_RESULT_DIR}'."
            script.sh(
                script: """
                    ${logger.shellScriptDebugFlag}
                    cp -rf ${xUnitResultDir}/* ${XUNIT_SYSTEM_RESULT_DIR} | true
                """,
                label: "Moving test results to system location: ${XUNIT_SYSTEM_RESULT_DIR}"
            )
        }

        def foundTests = 0
        script.dir (XUNIT_SYSTEM_RESULT_DIR) {
            foundTests = script.findFiles(glob: '**/**.xml').size()
        }

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
            logger.info 'No xUnit results for stashing'
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

    // MEM leak saver! delete the previous run, if it was triggered by an RM skip commit
    void deletePreviousNotBuiltBuild () {
        def previousBuild = script.currentBuild.getPreviousBuild()
        if (previousBuild?.getResult()?.toString() == 'NOT_BUILT') {
            def buildId = "${previousBuild.getId()}"
            logger.debug("Found previous CI SKIP run: ${buildId}, ${previousBuild.getDescription()}")
            // get the change set(s) and look for the first (== last commit and its message)
            if (!previousBuild.getChangeSets()?.isEmpty()) {
                ChangeLogSet changes = previousBuild.getChangeSets().get(0)
                if (!changes.isEmptySet()) {
                    ChangeLogSet.Entry change = changes.getItems()[0]
                    logger.debug("Changlog message: ${change.getMsg()}")
                    if (change.getMsg()?.startsWith('ODS: Export OpenShift configuration')) {
                        try {
                            previousBuild.getRawBuild().delete()
                            logger.info("Deleted (previous) build: ${buildId} because it was autogenerated by RM")
                        } catch (err) {
                            logger.warn ("Could not delete build with id: '${buildId}', ${err}")
                        }
                    } else {
                        logger.debug("Found human changelog: \n${change.getMsg()}, hence previous build will not be deleted')")
                    }
                }
            }
        }
    }
}
