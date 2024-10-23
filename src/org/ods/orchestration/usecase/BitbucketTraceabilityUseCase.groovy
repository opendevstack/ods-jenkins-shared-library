package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.util.Project
import org.ods.orchestration.util.StringCleanup
import org.ods.services.BitbucketService
import org.ods.util.IPipelineSteps

import java.text.SimpleDateFormat

class BitbucketTraceabilityUseCase {

    private static final int PAGE_LIMIT = 10
    protected static Map CHARACTER_REMOVEABLE = [
        '/': '/\u200B',
        '@': '@\u200B',
    ]

    private final BitbucketService bitbucketService
    private final IPipelineSteps steps
    private final Project project

    BitbucketTraceabilityUseCase(BitbucketService bitbucketService, IPipelineSteps steps, Project project) {
        this.steps = steps
        this.project = project
        this.bitbucketService = bitbucketService
    }

    /**
     * Obtain the information of all the merged pull requests for all the project repositories.
     * @return a Map with the pull request information for each entry found.
     */
    List<Map> getPRMergeInfo() {
        String token = bitbucketService.getToken()
        return obtainMergeInfo(token)
    }

    @NonCPS
    private List<Map> obtainMergeInfo(String token) {
        List<Record> records = processRepositories(token)
        int numRecords = records.size()
        def mergeInfo = new ArrayList(numRecords)
        for (int i = 0; i < numRecords; i++) {
            def record = records[i]
            def entry = [
                date: record.commitDate,
                authorName: sanitize(record.author.name),
                authorEmail: sanitize(record.author.mail),
                reviewers: obtainReviewers(record),
                url: sanitize(record.mergeRequestURL),
                commit: record.mergeCommitSHA,
                component: record.componentName,
            ]
            mergeInfo << entry
        }
        return mergeInfo
    }

    @NonCPS
    private List<Map> obtainReviewers(Record record) {
        List<Developer> reviewers = record.reviewers
        int numReviewers = reviewers.size()
        def reviewerInfo = new ArrayList(numReviewers)
        for (int i = 0; i < numReviewers; i++) {
            def reviewer = reviewers[i]
            def entry = [
                reviewerName: sanitize(reviewer.name),
                reviewerEmail: sanitize(reviewer.mail),
            ]
            reviewerInfo << entry
        }
        return reviewerInfo
    }

    @NonCPS
    private String sanitize(String s) {
        return s ? StringCleanup.removeCharacters(s, CHARACTER_REMOVEABLE) : 'N/A'
    }

    @NonCPS
    private List<Record> processRepositories(String token) {
        def records = []
        List<Map> repos = getRepositories()
        int reposSize = repos.size()
        for (def i = 0; i < reposSize; i++) {
            def repo = repos[i]
            records += processRepo(token, repo)
        }
        return records
    }

    @NonCPS
    private List<Map> getRepositories() {
        List<Map> result = []
        List<Map> repos = this.project.getRepositories()
        int reposSize = repos.size()
        for (def i = 0; i < reposSize; i++) {
            def repository = repos[i]
            result << [repo: "${project.data.metadata.id.toLowerCase()}-${repository.id}",
                       defaultBranch: repository.defaultBranch]
        }
        return result
    }

    @NonCPS
    private List<Record> processRepo(String token, Map repo) {
        def records = []
        boolean nextPage = true
        int nextPageStart = 0
        while (nextPage) {
            Map commits = bitbucketService.getCommitsForIntegrationBranch(token, repo.repo, PAGE_LIMIT, nextPageStart)
            if (commits.isLastPage) {
                nextPage = false
            } else {
                nextPageStart = commits.nextPageStart
            }
            records += processCommits(token, repo, commits)
        }
        return records
    }

    @NonCPS
    private List<Record> processCommits(String token, Map repo, Map commits) {
        return commits.values.collect { commit ->
            Map mergedPR = bitbucketService.getPRforMergedCommit(token, repo.repo, commit.id)
            // Only changes in PR and destiny integration branch
            if (mergedPR.values
                && mergedPR.values[0].toRef.displayId == repo.defaultBranch) {
                def record = new Record(getDateWithFormat(commit.committerTimestamp),
                    getAuthor(commit.author),
                    getReviewers(mergedPR.values[0].reviewers),
                    mergedPR.values[0].links.self[(0)].href,
                    commit.id,
                    repo.repo)
                return record
            }
            return null
        }.findAll { it != null }
    }

    @NonCPS
    private Developer getAuthor(Map author) {
        return new Developer(
            author.name,
            author.emailAddress)
    }

    @NonCPS
    private List getReviewers(List reviewers) {
        List<Developer> approvals = []
        reviewers.each {
            if (it.approved) {
                approvals << new Developer(
                    it.user.name,
                    it.user.emailAddress)
            }
        }

        return approvals
    }

    @NonCPS
    private String getDateWithFormat(Long timestamp) {
        Date dateObj =  new Date(timestamp)
        return new SimpleDateFormat('yyyy-MM-dd', Locale.getDefault()).format(dateObj)
    }

    private class Record {

        String commitDate
        Developer author
        List<Developer> reviewers
        String mergeRequestURL
        String mergeCommitSHA
        String componentName

        @SuppressWarnings(['ParameterCount'])
        Record(String date, Developer author, List<Developer> reviewers, String mergeRequestURL,
               String mergeCommitSHA, String componentName) {
            this.commitDate = date
            this.author = author
            this.reviewers = reviewers
            this.mergeRequestURL = mergeRequestURL
            this.mergeCommitSHA = mergeCommitSHA
            this.componentName = componentName
        }

    }

    private class Developer {

        String name
        String mail

        Developer(String name, String mail) {
            this.name = name
            this.mail = mail
        }

    }

}
