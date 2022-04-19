package org.ods.core.test.usecase.levadoc.fixture

import org.junit.rules.TemporaryFolder
import org.ods.core.test.LoggerStub
import org.ods.core.test.service.BitbucketServiceForWiremock
import org.ods.orchestration.util.Project

import static groovy.json.JsonOutput.prettyPrint
import static groovy.json.JsonOutput.toJson

class ProjectRepositoryFixture {

    private static final String FILENAMES_PATH = "test/resources/projectRepositoriesFixtures"

    private Map repo
    private Project project
    private BitbucketServiceForWiremock bitbucketService
    private TemporaryFolder tempFolder
    private LoggerStub loggerStub

    ProjectRepositoryFixture(Map repo, Project project, BitbucketServiceForWiremock bitbucketService, TemporaryFolder tempFolder,
                             LoggerStub loggerStub) {
        this.repo = repo
        this.project = project
        this.bitbucketService = bitbucketService
        this.tempFolder = tempFolder
        this.loggerStub = loggerStub
    }

    Map load() {

        String projectName = project.key
        String repoName = "${projectName}-${repo.id}"
        String bitbucketUrl = bitbucketService.url
        String bitbucketUrlTail = "/scm/${projectName}/${repoName}.git"

        URI repoUri = new URI("${bitbucketUrl}${bitbucketUrlTail}").normalize()
        String releaseBranchName = this.project.gitReleaseBranch

        def commitsInfo
        try {
            commitsInfo = bitbucketService.getBranchCommits(projectName, repoName, releaseBranchName, 2)
        } catch (RuntimeException e) {
            releaseBranchName = "master"
            commitsInfo = bitbucketService.getBranchCommits(projectName, repoName, releaseBranchName, 2)
        }
        loggerStub.info(prettyPrint(toJson(commitsInfo)))


        // in case of a re-checkout, scm.GIT_COMMIT  still points
        // to the old commit.
        def commit = commitsInfo.id
        def prevCommit = commitsInfo.parents[0].id
        def lastSuccessCommit = prevCommit


        repo.data.git = [
            branch: releaseBranchName,
            commit: commit,
            previousCommit: prevCommit,
            previousSucessfulCommit: lastSuccessCommit,
            url: repoUri.toString(),
            baseTag: this.project.baseTag,
            targetTag: this.project.targetTag,
            createdExecutionCommit: ""
        ]

    }

}
