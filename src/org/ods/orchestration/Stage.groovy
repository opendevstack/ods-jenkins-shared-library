package org.ods.orchestration

import org.ods.services.ServiceRegistry
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.usecase.JUnitTestReportsUseCase
import org.ods.services.BitbucketService
import org.ods.services.GitService
import org.ods.services.JenkinsService

import org.ods.util.GitCredentialStore
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps

import org.ods.util.Logger
import org.ods.util.ILogger

class Stage {

    protected final def script
    protected final Project project
    protected final List<Set<Map>> repos
    protected final String startAgentStageName

    protected IPipelineSteps steps
    protected ILogger logger

    public final String STAGE_NAME = 'NOT SET'

    Stage(def script, Project project, List<Set<Map>> repos, String startAgentStageName = 'Init') {
        this.script = script
        this.project = project
        this.repos = repos
        this.startAgentStageName = startAgentStageName ?: ''
    }

    def execute() {
        setServices()

        this.steps.stage(STAGE_NAME) {
            this.logger.infoClocked ("${STAGE_NAME}", '**** STARTING orchestration stage ****')
            try {
                return this.run()
            } catch (e) {
                def eThrow = e
                // Check for random null references which occur after a Jenkins restart
                if (ServiceRegistry.instance == null || ServiceRegistry.instance.get(PipelineSteps) == null) {
                    eThrow = new IllegalStateException(
                        'Error: invalid references have been detected for critical pipeline services. ' +
                        'Most likely, your Jenkins instance has been recycled. Please re-run the pipeline!'
                    ).initCause(e)
                }

                this.logger.warn("Error occured within the orchestration pipeline: ${e.message}")

                try {
                    project.reportPipelineStatus(eThrow.message, true)
                } catch (reportError) {
                    this.logger.warn("Error: unable to report pipeline status because of: ${reportError.message}.")
                    reportError.initCause(e)
                    throw reportError
                }

                throw eThrow
            } finally {
                this.logger.infoClocked ("${STAGE_NAME}", '**** ENDED orchestration stage ****')
            }
        }
    }

    @SuppressWarnings('GStringAsMapKey')
    def executeInParallel (Closure block1, Closure block2) {
        Map executors = [
            "${STAGE_NAME}": {
                block1()
            },
            'orchestration': {
                block2()
                this.logger.debug("Current stage: '${STAGE_NAME}' -> " +
                    "start agent stage: '${startAgentStageName}'")
                if (startAgentStageName.equalsIgnoreCase(STAGE_NAME)) {
                    def podLabel = "mro-jenkins-agent-${this.steps.env.BUILD_NUMBER}"
                    this.logger.debugClocked(podLabel)
                    this.steps.node(podLabel) {
                        this.logger.debugClocked(podLabel)
                    }
                }
            },
        ]
        executors.failFast = true
        this.steps.parallel(executors)
    }

    Map getTestResults(def steps, Map repo, String type = 'unit') {
        def jenkins = ServiceRegistry.instance.get(JenkinsService)
        def junit = ServiceRegistry.instance.get(JUnitTestReportsUseCase)

        type = type.toLowerCase()
        def testReportsPath = "${PipelineUtil.XUNIT_DOCUMENTS_BASE_DIR}/${repo.id}/${type}"

        this.logger.debug("Collecting JUnit XML Reports ('${type}') for ${repo.id}")
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
        if (!hasStashedTestReports) {
            throw new RuntimeException(
                "Error: unable to unstash JUnit XML reports, type '${type}' for repo '${repo.id}' " +
                "from stash '${testReportsStashName}'."
            )
        }

        def testReportFiles = junit.loadTestReportsFromPath(testReportsUnstashPath)

        return [
            // Load JUnit test report files from path
            testReportFiles: testReportFiles,
            // Parse JUnit test report files into a report
            testResults: junit.parseTestReportFiles(testReportFiles),
        ]
    }

    // Retrieve services from ServiceRegistry.
    // This is not in the constructor because of Jenkins limitations,
    // see https://www.jenkins.io/doc/book/pipeline/cps-method-mismatches/.
    protected void setServices() {
        def s = ServiceRegistry.instance.get(PipelineSteps)
        if (!s) {
            s = new PipelineSteps(this.script)
            ServiceRegistry.instance.add(PipelineSteps, s)
        }
        this.steps = s
        this.logger = ServiceRegistry.instance.get(Logger)
    }

    protected def runOnAgentPod(boolean condition, Closure block) {
        if (condition) {
            def bitbucket = ServiceRegistry.instance.get(BitbucketService)
            def git = ServiceRegistry.instance.get(GitService)
            this.logger.startClocked("${project.key}-${STAGE_NAME}-stash")
            this.steps.dir(this.steps.env.WORKSPACE) {
                this.steps.stash(name: 'wholeWorkspace', includes: '**/*,**/.git', useDefaultExcludes: false)
            }
            this.logger.debugClocked("${project.key}-${STAGE_NAME}-stash")
            def podLabel = "mro-jenkins-agent-${this.steps.env.BUILD_NUMBER}"
            this.logger.debugClocked(podLabel, 'Starting orchestration pipeline agent pod')
            this.steps.node(podLabel) {
                this.logger.debugClocked(podLabel)
                git.configureUser()
                this.logger.startClocked("${project.key}-${STAGE_NAME}-unstash")
                this.steps.unstash('wholeWorkspace')
                this.logger.debugClocked("${project.key}-${STAGE_NAME}-unstash")
                this.steps.withCredentials(
                    [this.steps.usernamePassword(
                        credentialsId: bitbucket.passwordCredentialsId,
                        usernameVariable: 'BITBUCKET_USER',
                        passwordVariable: 'BITBUCKET_PW'
                    )]
                ) {
                    GitCredentialStore.configureAndStore(
                        script,
                        bitbucket.url as String,
                        this.steps.env.BITBUCKET_USER as String,
                        this.steps.env.BITBUCKET_PW as String,
                    )
                }
                block()
            }
        } else {
            block()
        }
    }

}
