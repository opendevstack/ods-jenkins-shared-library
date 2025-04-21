package org.ods.orchestration

import com.cloudbees.groovy.cps.NonCPS

import org.ods.services.ServiceRegistry
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.JenkinsService

import org.ods.util.GitCredentialStore
import org.ods.util.IPipelineSteps

import org.ods.util.Logger
import org.ods.util.ILogger

class Stage {

    protected def script
    protected Project project
    protected List<Set<Map>> repos
    String startAgentStageName

    public final String STAGE_NAME = 'NOT SET'

    Stage(def script, Project project, List<Set<Map>> repos, String startAgentStageName = 'Init') {
        this.script = script
        this.project = project
        this.repos = repos
        this.startAgentStageName = startAgentStageName ?: ''
    }

    def execute() {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        def steps = ServiceRegistry.instance.get(IPipelineSteps)
        script.stage(STAGE_NAME) {
            logger.infoClocked ("${STAGE_NAME}", '**** STARTING orchestration stage ****')
            try {
                logger.debug("BEGIN Calling ${this.class.name}.run()")
                return this.run()
            } catch (e) {
                def eThrow = e
                // Check for random null references which occur after a Jenkins restart
                if (ServiceRegistry.instance == null || ServiceRegistry.instance.get(IPipelineSteps) == null) {
                    eThrow = new IllegalStateException(
                        'Error: invalid references have been detected for critical pipeline services. ' +
                        'Most likely, your Jenkins instance has been recycled. Please re-run the pipeline!'
                    ).initCause(e)
                }

                logger.warn("Error occured within the orchestration pipeline (${this.class.name}): ${e.message}")

                try {
                    project.reportPipelineStatus(getTestResultsForAllRepos(steps), eThrow.message, true)
                } catch (reportError) {
                    logger.warn("Error: unable to report pipeline status because of: ${reportError.message}.")
                    reportError.initCause(e)
                    throw reportError
                }

                throw eThrow
            } finally {
                logger.debug("END Calling ${this.class.name}.run()")
                logger.infoClocked ("${STAGE_NAME}", '**** ENDED orchestration stage ****')
            }
        }
    }

    def getTestResultsForAllRepos(steps) {
        List TYPES = [Project.TestType.INSTALLATION, Project.TestType.INTEGRATION, Project.TestType.ACCEPTANCE, Project.TestType.UNIT]
        Map data = [:]
        data.tests = [:]
        TYPES.each { type ->
            repos.each { repo ->
                data.tests << [(type.toLowerCase()): getTestResults(steps, repo, type.toLowerCase())]
            }
        }
        return data
    }

    @SuppressWarnings('GStringAsMapKey')
    def executeInParallel (Closure block1, Closure block2) {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        Map executors = [
            "${STAGE_NAME}": {
                block1()
            },
            'orchestration': {
                block2()
                logger.debug("Current stage: '${STAGE_NAME}' -> " +
                    "start agent stage: '${startAgentStageName}'")
                if (startAgentStageName.equalsIgnoreCase(STAGE_NAME)) {
                    def podLabel = "mro-jenkins-agent-${script.env.BUILD_NUMBER}"
                    logger.debugClocked(podLabel)
                    script.node (podLabel) {
                        logger.debugClocked(podLabel)
                    }
                }
            },
        ]
        executors.failFast = true
        script.parallel (executors)
    }

    Map getTestResults(def steps, Map repo, String type = 'unit') {
        def jenkins = ServiceRegistry.instance.get(JenkinsService)
        def junit = ServiceRegistry.instance.get(JUnitTestReportsUseCase)
        ILogger logger = ServiceRegistry.instance.get(Logger)

        type = type.toLowerCase()
        def testReportsPath = "${PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR}/${repo.id}/${type}"

        logger.debug("Collecting JUnit XML Reports ('${type}') for ${repo.id}")

        def testReportsStashName = "test-reports-junit-xml-${repo.id}-${steps.env.BUILD_ID}"
        if (type != 'unit') {
            testReportsStashName = "${type}-${testReportsStashName}"
        }

        def testReportsUnstashPath = "${steps.env.WORKSPACE}/${testReportsPath}"
        def hasStashedTestReports = jenkins.unstashFilesIntoPath(
            testReportsStashName,
            testReportsUnstashPath,
            'JUnit XML Report'
        )

        def testReportFiles = []
        if (!hasStashedTestReports) {
            logger.warn(
                "Unable to unstash JUnit XML reports, type '${type}' for repo '${repo.id}' " +
                "from stash '${testReportsStashName}'."
            )
        } else {
            testReportFiles = junit.loadTestReportsFromPath(testReportsUnstashPath)
        }

        return [
            // Load JUnit test report files from path
            testReportFiles: testReportFiles,
            // Parse JUnit test report files into a report
            testResults: junit.parseTestReportFiles(testReportFiles),
        ]
    }

    Map getLogReports(def steps, Map repo, String type) {
        def jenkins = ServiceRegistry.instance.get(JenkinsService)
        ILogger logger = ServiceRegistry.instance.get(Logger)

        logger.debug("Collecting Logs Reports ('${type}') for ${repo.id}")
        def logsStashName = "${type}-${repo.id}-${steps.env.BUILD_ID}"
        def logsPath = "${PipelineUtil.LOGS_BASE_DIR}/${repo.id}"
        def logsUnstashPath = "${steps.env.WORKSPACE}/${logsPath}/${type}"

        def hasStashedLogs = jenkins.unstashFilesIntoPath(logsStashName, logsUnstashPath, 'Logs')
        if (!hasStashedLogs) {
            throw new RuntimeException(
                "Error: unable to unstash Log reports, type '${type}' for repo '${repo.id}'" +
                " from stash '${logsStashName}'."
            )
        }

        def logFiles = loadLogFilesFromPath(logsUnstashPath)
        if (!logFiles) {
            throw new RuntimeException(
                "Error: unable to load Log reports, type '${type}' for repo '${repo.id}'" +
                " from stash '${logsUnstashPath}'."
            )
        }

        def logs = logFiles.collect { file ->
            file ? file.text : ""
        }

        return [content: logs]
    }

    @NonCPS
    @SuppressWarnings(['EmptyCatchBlock', 'JavaIoPackageAccess'])
    protected List<File> loadLogFilesFromPath(String path) {
        def result = []

        try {
            new File(path).traverse(nameFilter: ~/.*\.log$/, type: groovy.io.FileType.FILES) { file ->
                result << file
            }
        } catch (FileNotFoundException e) { }

        return result
    }

    protected def runOnAgentPod(boolean condition, Closure block) {
        ILogger logger = ServiceRegistry.instance.get(Logger)
        if (condition) {
            def bitbucket = ServiceRegistry.instance.get(BitbucketService)
            def git = ServiceRegistry.instance.get(GitService)
            logger.startClocked("${project.key}-${STAGE_NAME}-stash")
            script.dir(script.env.WORKSPACE) {
                script.stash(name: 'wholeWorkspace', includes: '**/*,**/.git', useDefaultExcludes: false)
            }
            logger.debugClocked("${project.key}-${STAGE_NAME}-stash")
            def podLabel = "mro-jenkins-agent-${script.env.BUILD_NUMBER}"
            logger.debugClocked(podLabel, 'Starting orchestration pipeline agent pod')
            script.node(podLabel) {
                logger.debugClocked(podLabel)
                git.configureUser()
                logger.startClocked("${project.key}-${STAGE_NAME}-unstash")
                script.unstash('wholeWorkspace')
                logger.debugClocked("${project.key}-${STAGE_NAME}-unstash")
                script.withCredentials(
                    [script.usernamePassword(
                        credentialsId: bitbucket.passwordCredentialsId,
                        usernameVariable: 'BITBUCKET_USER',
                        passwordVariable: 'BITBUCKET_PW'
                    )]
                ) {
                    GitCredentialStore.configureAndStore(
                        script, bitbucket.url as String,
                        script.env.BITBUCKET_USER as String,
                        script.env.BITBUCKET_PW as String)
                }
                block()
            }
        } else {
            block()
        }
    }

}
