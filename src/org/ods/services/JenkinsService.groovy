package org.ods.services

import com.cloudbees.groovy.cps.NonCPS
import org.ods.util.ILogger
import hudson.scm.ChangeLogSet

class JenkinsService {

    private static final String XUNIT_SYSTEM_RESULT_DIR = 'build/test-results/test'
    private static final int DEFAULT_MAX_LOG_LENGTH = 2_000_000_000

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
                testResults: "${XUNIT_SYSTEM_RESULT_DIR}/**/**.xml",
                allowEmptyResults: true
            )

            def testStashPath = "test-reports-junit-xml-${stashNamePostFix}"
            contextresultMap.xunitTestResultsStashPath = testStashPath
            script.stash(
                name: "${testStashPath}",
                includes: "${XUNIT_SYSTEM_RESULT_DIR}/**/*.xml",
                allowEmpty: true
            )
            script.dir (XUNIT_SYSTEM_RESULT_DIR) {
                foundTests = script.findFiles(glob: '**/*.pdf').size()
            }
            logger.debug "Found ${foundTests} test PDF reports in '${XUNIT_SYSTEM_RESULT_DIR}'"
            if (foundTests.toInteger() > 0) {
                testStashPath = "test-reports-junit-pdf-${stashNamePostFix}"
                contextresultMap.evidencesStashPath = testStashPath
                script.dir(XUNIT_SYSTEM_RESULT_DIR) {
                    script.stash(
                        name: "${testStashPath}",
                        includes: '**/*.pdf',
                        allowEmpty: true
                    )
                }
            }
        } else {
            logger.info 'No xUnit results for stashing'
        }

        return contextresultMap
    }

    /**
     * It returns the length in bytes of the current log.
     * This value can be used as an offset to read the log from that position.
     *
     * @return the length of the current log in bytes.
     */
    long getCurrentLogLength() {
        return script.currentBuild.rawBuild.logText.length()
    }

    String getCurrentBuildLogAsHtml() {
        return getLogAsHtml(script.currentBuild.rawBuild)
    }

    @NonCPS
    static String getLogAsHtml(rawBuild) {
        return getLog({ start, writer -> rawBuild.logText.writeHtmlTo(start, writer) })
    }

    /**
     * Returns the current log as text. if {@code maxLength} is specified,
     * the returned log may be truncated.
     * Note that the maximum length is just a hint.
     * If the current log is longer, at least {@code maxLength} bytes will be returned
     * and no more bytes will be requested. However, the underlying API may return
     * a longer log, perhaps all of it.
     * If you need it truncated exactly to that size, you can always truncate the returned
     * value with substring.
     *
     * @param maxLength the maximum desired log length in bytes.
     * @return the log, possibly truncated.
     */
    String getCurrentBuildLogAsText (long offset = 0L, int maxLength = DEFAULT_MAX_LOG_LENGTH) {
        return getLogAsText(script.currentBuild.rawBuild, offset, maxLength)
    }

    @NonCPS
    static String getLogAsText(rawBuild, long offset = 0L, int maxLength = DEFAULT_MAX_LOG_LENGTH) {
        return getLog({ start, writer -> rawBuild.logText.writeLogTo(start, writer) }, offset, maxLength)
    }

    @NonCPS
    private static String getLog(Closure<Long> writeLogTo, long offset = 0L, int maxLength = DEFAULT_MAX_LOG_LENGTH) {
        if (offset < 0L) {
            throw new IllegalArgumentException("offset == ${offset}")
        }
        if (maxLength < 0) {
            throw new IllegalArgumentException("maxLength == ${maxLength}")
        }
        long limit = offset + maxLength
        if (limit < 0) {
            throw new IllegalArgumentException("offset + maxLength == ${limit}")
        }
        StringWriter writer = new StringWriter()
        while (offset < limit) {
            long newPos = writeLogTo(offset, writer)
            if (newPos > limit || newPos == offset) {
                break
            }
            offset = newPos
        }
        def log = writer.toString()
        int length = log.length()
        if (length > maxLength) {
            def newLine = System.lineSeparator()
            def newLinePos = log.indexOf(newLine, Math.max(maxLength - newLine.length(), 0))
            if (newLinePos >= 0) {
                maxLength = newLinePos
            }
            if (maxLength < length) {
                log = log.substring(0, maxLength)
            }
        }
        return log
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
    void deleteNotBuiltBuilds (def previousBuild = null) {
        if (!previousBuild) {
            return
        }
        // we need to do this super early - similar to the id, because once deleted - no access
        // option2 -reset the result to SUCCESS
        def previousMinusOneBuild = previousBuild.previousBuild
        if (previousBuild?.result?.toString() == 'NOT_BUILT') {
            def buildId = "${previousBuild.getId()}"
            logger.debug("Found CI SKIP run: ${buildId}, ${previousBuild.getDescription()}")
            // get the change set(s) and look for the first (== last commit and its message)
            if (!previousBuild.getChangeSets()?.isEmpty()) {
                ChangeLogSet changes = previousBuild.getChangeSets().get(0)
                if (!changes.isEmptySet()) {
                    ChangeLogSet.Entry change = changes.getItems()[0]
                    logger.debug("Changlog message: ${change.getMsg()}")
                    if (change.getMsg()?.startsWith('ODS: Export OpenShift configuration') ||
                        change.getMsg()?.startsWith('ODS: Export Openshift deployment state')) {
                        try {
                            previousBuild.getRawBuild().delete()
                            logger.info("Deleted (CI SKIP) build: '${buildId}' because it was autogenerated by RM")
                        } catch (err) {
                            logger.warn ("Could not delete build with id: '${buildId}', ${err}")
                        }
                    } else {
                        logger.debug("Found human changelog: \n${change.getMsg()}, " +
                            "hence build '${buildId}' will not be deleted")
                    }
                }
            }
        }
        // call this recursively to clean-up all the rm created builds
        deleteNotBuiltBuilds (previousMinusOneBuild)
    }

}
