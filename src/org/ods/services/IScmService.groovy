package org.ods.services

interface IScmService {
    // getters
    String getUrl()

    String getPasswordCredentialsId()

    String getToken()

    // reviewers
    String getDefaultReviewerConditions(String repo)

    List<String> getDefaultReviewers(String repo)

    // pull requests
    String createPullRequest(String repo, String fromRef, String toRef, String title, String description, List<String> reviewers)

    String getPullRequestsForCommit(String repo, String commit)

    String getPullRequests(String repo, String state)

    Map findPullRequest(String repo, String branch)

    Map findPullRequest(String repo, String branch, String state)

    void postComment(String repo, int pullRequestId, String comment)

    Map getMergedPullRequestsForIntegrationBranch(String token, Map repo, int limit, int nextPageStart)

    Map getCommmitsForPullRequest(String token, String repo, int pullRequest, int limit, int nextPageStart)

    // tags
    void postTag(String repo, String startPoint, String tag, Boolean force, String message)

    // build status
    void setBuildStatus(String buildUrl, String gitCommit, String state, String buildName)

    // branches
    String getDefaultBranch(String repo)

    Map findRepoBranches(String repo, String filterText)

    // “code insight”
    void createCodeInsightReport(Map data, String repo, String gitCommit)

    // creds
    def withTokenCredentials(Closure block)

    Map<String, String> createUserToken()
}
