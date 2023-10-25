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
        def failedRepos = repos.flatten().findAll { it.data?.failedStage }
        if (project.hasFailingTests() || failedRepos.size > 0) {
            def errMessage = "Failing build as repositories contain errors!" +
                "\nFailed repositories: \n${sanitizeFailedRepos(failedRepos)}"
            util.failBuild(errMessage)

            def tailorFailedReposCommaSeparated = findReposWithTailorFailureCommaSeparated(failedRepos)
            if (tailorFailedReposCommaSeparated?.length() > 0) {
                errMessage += "\n\nERROR: " + buildTailorMessageForJira(tailorFailedReposCommaSeparated);
            }
            // If we are not in Developer Preview raise a exception
            if (!project.isWorkInProgress) {
                throw new IllegalStateException(errMessage)
            }
        }

        if (project.isWorkInProgress) {
            def reposWithTailorDeploymentWarnCommaSeparated =
                findReposWithTailorWarnCommaSeparated(project.repositories)
            project.addCommentInReleaseStatus("WARNING: " +
                buildTailorMessageForJira(reposWithTailorDeploymentWarnCommaSeparated))
        }
    }

    String buildTailorMessageForJira(def failedRepoNamesCommaSeparated) {
        "We detected an undesired configuration drift. " +
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
            "\t1. Follow the link below to review the differences we found.\n" +
            "\t2. Please update your configuration stored in Bitbucket or the configuration " +
            "in the target environment as needed so that they match."
    }

    String sanitizeFailedRepos(def failedRepos) {
        def index = 1
        def sanitizedRepos = failedRepos.collect {it -> (index++) + ".\tRepository id: " + it.id +
            "\n\tBranch: " + it.branch + "\n\tRepository type: " + it.type }
            .join("\n\n");
        return sanitizedRepos
    }

    String findReposWithTailorFailureCommaSeparated(def allFailedRepos) {
        def tailorDeploymentFailedReposString = allFailedRepos
            .findAll {it -> it.data?.openshift?.tailorFailure}
            .collect {it -> "\"" + it.id + "\""}
            .join(", ")

        return tailorDeploymentFailedReposString
    }

    String findReposWithTailorWarnCommaSeparated(def repositories) {
        def tailorDeploymentWarnReposString = repositories
            .findAll {it -> it.tailorWarning}
            .collect {it -> "\"" + it.id + "\""}
            .join(", ")

        return tailorDeploymentWarnReposString
    }
}
