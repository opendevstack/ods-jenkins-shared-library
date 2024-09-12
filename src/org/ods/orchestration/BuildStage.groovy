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

    private static final String VULNERABILITY_NAME_PLACEHOLDER = "<CVE>"

    private static final String SECURITY_VULNERABILITY_ISSUE_SUMMARY = "Remotely exploitable security " +
        "vulnerability with solution detected by Aqua with name " + VULNERABILITY_NAME_PLACEHOLDER

    private static final String SECURITY_VULNERABILITY_ISSUE_PRIORITY = "Highest"

    private static final String JIRA_COMPONENT_TECHNOLOGY_PREFIX = 'Technology-'

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

            def aquaCriticalVulnerabilityRepos = filterReposWithAquaCriticalVulnerability(repos)
            if (aquaCriticalVulnerabilityRepos?.size() > 0) {
                def securityVulnerabilityIssueKeys = createSecurityVulnerabilityIssues(aquaCriticalVulnerabilityRepos)
                String aquaMessage = buildAquaSecurityVulnerabilityMessage(securityVulnerabilityIssueKeys)
                logMessage += aquaMessage
                jiraMessage += aquaMessage
            }

            util.failBuild(logMessage)
            // If we are not in Developer Preview or we have a Tailor failure or a Aqua remotely exploitable
            // vulnerability with solution found then raise an exception
            if (!project.isWorkInProgress || tailorFailedRepos?.size() > 0
                || aquaCriticalVulnerabilityRepos?.size() > 0) {
                throw new IllegalStateException(jiraMessage)
            }
        }
    }

    List createSecurityVulnerabilityIssues(List aquaCriticalVulnerabilityRepos) {
        def securityVulnerabilityIssueKeys = [];
        try {
            for (def repo : aquaCriticalVulnerabilityRepos) {
                def jiraComponentId = getJiraComponentId(repo)
                for (def vulnerability : repo.data.openshift.aquaCriticalVulnerability) {
                    def vulerabilityMap = vulnerability as Map
                    def issueKey = createOrUpdateSecurityVulnerabilityIssue(
                        vulerabilityMap.name,
                        jiraComponentId,
                        buildSecurityVulnerabilityIssueDescription(
                            vulerabilityMap,
                            repo.data.openshift.gitUrl,
                            repo.data.openshift.gitBranch,
                            repo.data.openshift.nexusReportLink))
                    securityVulnerabilityIssueKeys.add(issueKey)
                }
            }
        } catch (JiraNotPresentException e) {
            project.logger.warn(e.getMessage())
            return []
        }
        return securityVulnerabilityIssueKeys
    }

    String createOrUpdateSecurityVulnerabilityIssue(String vulnerabilityName, String jiraComponentId,
                                                    String description) {
        if (!project.jiraUseCase || !project.jiraUseCase.jira) {
            throw new JiraNotPresentException("JiraUseCase not present, cannot create security vulnerability issue.")
        }

        def issueSummary =  SECURITY_VULNERABILITY_ISSUE_SUMMARY.replace(VULNERABILITY_NAME_PLACEHOLDER,
            vulnerabilityName)

        def fixVersion = null
        if (project.isVersioningEnabled) {
            fixVersion = project.getVersionName()
        }
        def fullJiraComponentName = JIRA_COMPONENT_TECHNOLOGY_PREFIX + jiraComponentId

        List securityVulnerabilityIssues = project?.jiraUseCase?.jira?.loadJiraSecurityVulnerabilityIssues(issueSummary,
            fixVersion, fullJiraComponentName, project.jiraProjectKey)
        if (securityVulnerabilityIssues?.size() >= 1) { // Transition the issue to "TO DO" state
            transitionIssueToToDo(securityVulnerabilityIssues.get(0).id)
            return (securityVulnerabilityIssues.get(0) as Map)?.key
        } else { // Create the issue
            return (createIssueTypeSecurityVulnerability(fixVersion, fullJiraComponentName,
                SECURITY_VULNERABILITY_ISSUE_PRIORITY, projectKey: project.jiraProjectKey, summary: issueSummary,
                description: description)
                as Map)?.key
        }
    }

    Map createIssueTypeSecurityVulnerability(Map args, String fixVersion = null, String component = null,
                                             String priority = null) {
        return project?.jiraUseCase?.jira?.createIssue(fixVersion, component, priority, summary: args.summary,
            type: "Security Vulnerability", projectKey: args.projectKey, description: args.description)
    }


    void transitionIssueToToDo(String issueId) {
        int maxAttemps = 10;
        while (maxAttemps-- > 0) {
            def possibleTransitions = project?.jiraUseCase?.jira?.getTransitions(issueId)
            Map possibleTransitionsByName = possibleTransitions
                .collectEntries { t -> [t.name.toString().toLowerCase(), t] }
            if (possibleTransitionsByName.containsKey("confirm dor")) { // Issue is already in TO DO state
                return
            } else if (possibleTransitionsByName.containsKey("implement")) { // We need to transiton the issue
                project?.jiraUseCase?.jira?.doTransition(issueId, possibleTransitionsByName.get("implement"))
                continue
            } else if (possibleTransitionsByName.containsKey("confirm dod")) { // We need to transiton the issue
                project?.jiraUseCase?.jira?.doTransition(issueId, possibleTransitionsByName.get("confirm dod"))
                continue
            } else if (possibleTransitionsByName.containsKey("reopen")) { // We need just one transiton
                project?.jiraUseCase?.jira?.doTransition(issueId, possibleTransitionsByName.get("reopen"))
                return
            } else {
                throw new IllegalStateException("Unexpected issue transition states " +
                    "found: ${possibleTransitionsByName.keySet()}")
            }
        }
        throw new IllegalStateException("The issue could not be transitioned to TODO state.")
    }

    String buildSecurityVulnerabilityIssueDescription(Map vulnerability, String gitUrl, String gitBranch,
                                                    String nexusReportLink) {
        StringBuilder message = new StringBuilder()
        message.append("\nh3.Aqua security scan detected the remotely exploitable critical " +
            "vulnerability with name '${vulnerability.name as String}' in ${gitUrl} in branch ${gitBranch}." )
        message.append("\n*Description:* " + vulnerability.description as String)
        message.append("\n\n*Solution:* " + vulnerability.solution as String)

        if (nexusReportLink != null) {
            message.append("\n\n*You can find the complete security scan report here:* " + nexusReportLink)
        }

        return message.toString()
    }

    String buildAquaSecurityVulnerabilityMessage(List securityVulnerabilityIssueKeys) {
        if (securityVulnerabilityIssueKeys?.size() == 0) { // No issue created as Jira is not connected
            return "\n\nRemotely exploitable critical vulnerabilities were detected (see above). " +
                "Due to their high severity, we must stop the delivery process until all vulnerabilities " +
                "have been addressed.\n"
        } else if (securityVulnerabilityIssueKeys?.size() == 1) {
            return "\n\nA remotely exploitable critical vulnerability was detected and documented in " +
                "the following Jira issue: ${securityVulnerabilityIssueKeys[0]}. Due to their high " +
                "severity, we must stop the delivery process until all vulnerabilities have been addressed.\n"
        } else {
            return "\n\nRemotely exploitable critical vulnerabilities were detected and documented in " +
                "the following Jira issues: ${securityVulnerabilityIssueKeys.join(", ")}. Due to their high " +
                "severity, we must stop the delivery process until all vulnerabilities have been addressed.\n"
        }
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

    String getJiraComponentId(def repo) {
        return repo.data?.openshift?.jiraComponentId
    }

    String buildReposCommaSeparatedString(def tailorFailedRepos) {
        def reposCommaSeparatedString = tailorFailedRepos
            .collect { it -> "\"" + it.id + "\"" }
            .join(", ")

        return reposCommaSeparatedString
    }

}
