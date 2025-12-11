package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.service.JiraService
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.MROPipelineUtil.PipelineConfig
import org.ods.services.BitbucketService
import org.ods.util.IPipelineSteps
import org.yaml.snakeyaml.Yaml

class BitbucketTraceabilityUseCase {

    private final BitbucketService bitbucketService
    private final JiraService jiraService
    private final IPipelineSteps steps
    private final Project project

    BitbucketTraceabilityUseCase(BitbucketService bitbucketService,
                                 JiraService jiraService,
                                 IPipelineSteps steps,
                                 Project project) {
        this.steps = steps
        this.project = project
        this.bitbucketService = bitbucketService
        this.jiraService = jiraService
    }

    @NonCPS
    String getBaseURL() {
        return bitbucketService.url
    }

    @NonCPS
    List<Map> getIncludedRepos() {
        return project.repositories?.findAll { repo -> repo.include }?.collect { repo ->
            repo + [name: "${project.key.toLowerCase()}-${repo.id}"]
        }?.sort { it.id }
    }

    Map<String, List<Map>> getPullRequestInfo(List<Map> repos, String previousReleaseTagRef = null) {
        String token = bitbucketService.getToken()
        return addPRInfo(repos, token, previousReleaseTagRef)
    }

    @NonCPS
    private Map<String, List> addPRInfo(List<Map> repos, String token, String previousReleaseTagRef) {
        return repos.collectEntries { repo ->
            def name = repo.name as String
            def pullRequests = getPullRequests(token, name, null, previousReleaseTagRef)
            return pullRequests ? [ (name): pullRequests ] : [:]
        } as Map<String, List>
    }

    @NonCPS
    private List<Map> getPullRequests(String token, String repo, String branch, String previousReleaseTagRef) {
        long closedAfter = getPreviousReleaseTimestamp(token, repo, previousReleaseTagRef)
        def pullRequests = bitbucketService.getAllPullRequestsForRepo(token, repo, branch,
            'MERGED', closedAfter)
        return pullRequests.collect { pullRequest ->
            def prInfo = getPRInfo(pullRequest, token, repo)
            if (!prInfo.issues) {
                return null
            }
            prInfo << getApprovers(pullRequest)
            prInfo << getCommitInfo(pullRequest, token, repo)
            return prInfo
        }.findAll()
    }

    @NonCPS
    private static Map<String, Object> getApprovers(Map<String, Object> pullRequest) {
        def approvers = pullRequest.reviewers
            ?.findAll { Map reviewer -> reviewer.approved }
            ?.collectEntries { approver ->
                [
                    name: approver.user?.name,
                    email: approver.user?.emailAddress,
                ]
            }
        return approvers ? [ approvers: approvers ] : [:]
    }

    @NonCPS
    private Map<String, Object> getCommitInfo(Map<String, Object> pullRequest, String token, String repo) {
        def commits = bitbucketService.getAllCommitsForPullRequest(token, repo, pullRequest.id as String)
            .sort { commit -> commit.committerTimestamp }
        if (!commits) {
            return null
        }
        def commitInfo = [:] as Map
        commitInfo.commits = commits.collect { commit -> [ hash: commit.displayId ] }
        def authors =  commits*.author.findAll().collectEntries { author ->
            [(author.name): author.emailAddress]
        }.collect { name, email ->
            [
                name:  name,
                email: email,
            ]
        }
        commitInfo.commitAuthors = authors
        return commitInfo
    }

    @NonCPS
    private long getPreviousReleaseTimestamp(String token, String repo, String tagRef) {
        if (tagRef) {
            def tag = bitbucketService.getTag(token, repo, tagRef)
            if (tag.type != 'TAG') {
                throw new IllegalArgumentException("${tagRef} is a ${tag.type}, not a TAG")
            }
            def commit = bitbucketService.getCommit(token, repo, tag.latestCommit as String)
            return commit.committerTimestamp as long
        }
        return 0
    }

    @NonCPS
    private Map<String, Object> getPullRequestForCommit(String token, String repo, String hash, String branch) {
        def pullRequest = bitbucketService.getPullRequestForCommit(token, repo, hash, branch)
        return pullRequest ? getPRInfo(pullRequest, token, repo) : null
    }

    @NonCPS
    private Map<String, Object> getPRInfo(Map<String, Object> pullRequest, String token, String repo) {
        def issues = [:] as Map<String, Map>
        def requirements = [:] as Map<String, Map>
        def issueLinks = bitbucketService.getIssueLinksForPullRequest(token, repo, pullRequest.id as String)
        issueLinks.each { key, url ->
            def issue = project.issues[key] // Will be null, if the issue isn't in Done state.
            if (!issue || !belongsToChange(issue, project.buildParams.changeId as String)) {
                return
            }
            issues[issue.key as String] = [ summary: issue.fields?.summary ?: issue.key ]
            issue.remoteLinks?.each { Map link ->
                def linkURL = link.url
                def requirement = project.requirementsByURL[linkURL]
                if (requirement) {
                    requirements[linkURL.toString()] = [
                        title:
                            requirement.metadata.pageTitle ?: "Requirement: ${requirement.metadata.requirementNumber}"
                    ]
                }
            }
        }
        def prInfo = [
            id: pullRequest.id,
            url: pullRequest.links?.self?.first()?.href,
        ]
        def author = pullRequest.author?.user as Map
        if (author) {
            prInfo.author = [
                name: author.name,
                email: author.emailAddress,
            ]
        }
        if (issues) {
            prInfo.issues = issues
            if (requirements) {
                if (requirements.size() == 1) {
                    prInfo.requirement = requirements.values().first()
                } else {
                    prInfo.requirements = requirements.values()
                }
            }
        }
        return prInfo
    }

    @NonCPS
    private static boolean belongsToChange(Map<String, Object> issue, String changeId) {
        def fixVersions = issue?.fields?.fixVersions as List<Map>
        return fixVersions?.find { fixVersion -> fixVersion.name == changeId }
    }

    Map<String, Map> getODSComponentMetadata(String gitRevision) {
        Map<String, Map> result = [:]

        def token = bitbucketService.getToken()
        def filePath = "metadata.yml"

        bitbucketService.getFileContentsForProjectRepos(token, filePath, gitRevision).each { repoName, data ->
            // FIXME: potential code duplication from MROPipelineUtil::loadPipelineConfig()
            Map metadata = new Yaml().load(data.fileContents as String) ?: [:]
            if (metadata.description?.trim() && metadata.supplier?.trim()) {
                metadata.repo = data.repo
                def type = metadata.type
                def installable = type ? PipelineConfig.INSTALLABLE_REPO_TYPES.contains(type) : true
                metadata.installable = installable
                result[repoName] = metadata
            }
        }

        return result
    }

    Map<String, Map> getODSComponentDependencies(String gitRevision) {
        Map<String, Map> result = [:]

        def token = bitbucketService.getToken()
        def filePath = "release-manager.yml"

        bitbucketService.getFileContentsForProjectRepos(token, filePath, gitRevision).each { repoName, data ->
            Map metadata = new Yaml().load(data.fileContents as String) ?: [:]
            if (metadata.dependencies) {
                result[repoName] = metadata.dependencies
            }
        }

        return result
    }

}
