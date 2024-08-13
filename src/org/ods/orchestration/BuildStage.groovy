package org.ods.orchestration

import org.ods.orchestration.scheduler.LeVADocumentScheduler
import org.ods.orchestration.usecase.JiraUseCase
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PipelinePhaseLifecycleStage
import org.ods.orchestration.util.Project
import org.ods.services.ServiceRegistry
import org.ods.util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger

class BuildStage extends Stage {

    public static final String LOG_CUSTOM_PART = 'See the logs above'

    public static final String JIRA_CUSTOM_PART = 'Follow the link below'

    public final String STAGE_NAME = 'Build'

    BuildStage(def script, Project project, List<Set<Map>> repos, String startMROStageName) {
        super(script, project, repos, startMROStageName)
    }

    @SuppressWarnings(['ParameterName', 'AbcMetric'])
    def run() {
        def steps = ServiceRegistry.instance.get(PipelineSteps)
        def jira = ServiceRegistry.instance.get(JiraUseCase)
        def util = ServiceRegistry.instance.get(MROPipelineUtil)
        def levaDocScheduler = ServiceRegistry.instance.get(LeVADocumentScheduler)
        ILogger logger = ServiceRegistry.instance.get(Logger)

        def phase = MROPipelineUtil.PipelinePhases.BUILD

        def preExecuteRepo = { steps_, repo ->
            levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_EXECUTE_REPO, repo)
        }

        def postExecuteRepo = { steps_, repo ->
            // FIXME: we are mixing a generic scheduler capability with a data
            // dependency and an explicit repository constraint.
            // We should turn the last argument 'data' of the scheduler into a
            // closure that return data.
            if (project.isAssembleMode
                && repo.type?.toLowerCase() == MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE) {
                def data = [ : ]
                def resultsResurrected = !!repo.data.openshift.resurrectedBuild
                if (resultsResurrected) {
                    logger.info("[${repo.id}] Resurrected tests from run " +
                        "${repo.data.openshift.resurrectedBuild} " +
                        "- no unit tests results will be reported")
                } else {
                    data << [tests: [unit: getTestResults(steps, repo) ]]
                    jira.reportTestResultsForComponent(
                        "Technology-${repo.id}",
                        [Project.TestType.UNIT],
                        data.tests.unit.testResults
                    )
                    // we check in any case ... (largely because the above call will
                    // return immediatly when no jira adapter is configured).
                    // this  will set failedTests if any xunit tests have failed
                    util.warnBuildIfTestResultsContainFailure(data.tests.unit.testResults)
                }

                logger.debug("levaDocScheduler.run start")
                levaDocScheduler.run(
                    phase,
                    PipelinePhaseLifecycleStage.POST_EXECUTE_REPO,
                    repo,
                    data
                )
                logger.debug("levaDocScheduler.run end")
            }
        }

        // (cut) the reason to NOT go parallel here is a jenkins feature with too many
        // parallels causing arraylist$itr serioalouation errors
        levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.POST_START)

        util.prepareExecutePhaseForReposNamedJob(phase, repos, preExecuteRepo, postExecuteRepo)
            .each { group ->
                // FailFast only if not WIP
                group.failFast = !project.isWorkInProgress
                script.parallel(group)
            }

        levaDocScheduler.run(phase, PipelinePhaseLifecycleStage.PRE_END)

        // in case of WIP we fail AFTER all pieces have been executed - so we can report as many
        // failed unit tests as possible
        // - this will only apply in case of WIP! - otherwise failfast is configured, and hence
        // the build will have failed beforehand
        def failedRepos = repos?.flatten().findAll { it.data?.failedStage }
        if (project.hasFailingTests() || failedRepos?.size > 0) {
            def baseErrMsg = "Failing build as repositories contain errors!" +
                "\nFailed repositories:\n${sanitizeFailedRepos(failedRepos)}"

            def tailorFailedRepos = filterReposWithTailorFailure(failedRepos)
            def jiraMessage = baseErrMsg
            def logMessage = baseErrMsg
            if (tailorFailedRepos?.size() > 0) {
                String failedReposCommaSeparated = buildReposCommaSeparatedString(tailorFailedRepos)
                logMessage += buildTailorMessage(failedReposCommaSeparated, LOG_CUSTOM_PART)
                jiraMessage += buildTailorMessage(failedReposCommaSeparated, JIRA_CUSTOM_PART)
            }
            util.failBuild(logMessage)
            // If we are not in Developer Preview or we have a Tailor failure raise an exception
            if (!project.isWorkInProgress || tailorFailedRepos?.size() > 0) {
                throw new IllegalStateException(jiraMessage)
            }
        }
        def aquaCriticalVulnerabilityRepos = filterReposWithAquaCriticalVulnerability(repos)
        if (aquaCriticalVulnerabilityRepos?.size() > 0) {
            String aquaFiledMessage = "Aqua critical vulnerability with solution detected"
            project.createOrUpdateSecurityVulnerabilityIssue(
                buildSecurityVulnerabilityIssueDescription(aquaCriticalVulnerabilityRepos))
            util.failBuild(aquaFiledMessage)
            throw new IllegalStateException(aquaFiledMessage)
        }
    }

    String buildSecurityVulnerabilityIssueDescription(aquaCriticalVulnerabilityRepos) {
        StringBuilder message = new StringBuilder()
        message.append("h3. Aqua scan found the following remotely exploitable critical vulnerabilities with solutions " +
            "that need to be addressed:\n")
        def count = 1
        for (def repo : aquaCriticalVulnerabilityRepos) {
            message.append("\nh4. ${count++}. Vulnerabilities for repository *" + repo.id + "*")
            def vulnerabilitiesCount = 1
            for (def vulnerability : repo.data.openshift.aquaCriticalVulnerability) {
                message.append("\n*${count++}.${vulnerabilitiesCount} Vulnerability name: " + (vulnerability as Map).name as String)
                message.append("\n*description: " + (vulnerability as Map).description as String)
                message.append("\n*solution: " + (vulnerability as Map).solution as String)
                message.append("\n----")
            }
        }
        return message.toString()
    }

    String buildTailorMessage(String failedRepoNamesCommaSeparated, String customPart) {
        return "\n\nERROR: We detected an undesired configuration drift. " +
            "A drift occurs when " +
            "changes in a target environment are not covered by configuration files in Git " +
            "(regarded as the source of truth). Resulting differences may be due to manual " +
            "changes in the configuration of the target environment or automatic changes " +
            "performed by OpenShift/Kubernetes.\n" +
            "\n" +
            "We found drifts for the following components: " +
            "${failedRepoNamesCommaSeparated}.\n" +
            "\n" +
            "Please follow these steps to resolve and restart your deployment:\n" +
            "\n" +
            "\t1. " + customPart +
            " to review the differences we found.\n" +
            "\t2. Please update your configuration stored in Bitbucket or the configuration " +
            "in the target environment as needed so that they match."
    }

    String sanitizeFailedRepos(def failedRepos) {
        def index = 1
        def sanitizedRepos = failedRepos.collect { it ->
            (index++) + ".\tRepository id: " + it.id +
            "\n\tBranch: " + it.defaultBranch + "\n\tRepository type: " + it.type }
            .join("\n\n")
        return sanitizedRepos
    }

    List filterReposWithTailorFailure(def repos) {
        return repos?.flatten()?.findAll { it -> it.data?.openshift?.tailorFailure }
    }

    List filterReposWithAquaCriticalVulnerability(def repos) {
        return repos?.flatten()?.findAll { it -> it.data?.openshift?.aquaCriticalVulnerability }
    }

    String buildReposCommaSeparatedString(def tailorFailedRepos) {
        def reposCommaSeparatedString = tailorFailedRepos
            .collect { it -> "\"" + it.id + "\"" }
            .join(", ")

        return reposCommaSeparatedString
    }

}
