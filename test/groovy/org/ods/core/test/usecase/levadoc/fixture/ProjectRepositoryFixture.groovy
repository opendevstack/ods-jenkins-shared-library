package org.ods.core.test.usecase.levadoc.fixture

import org.junit.rules.TemporaryFolder
import org.ods.core.test.LoggerStub
import org.ods.core.test.service.BitbucketServiceForWiremock
import org.ods.core.test.usecase.RepoDataBuilder
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.Project
import org.ods.services.BitbucketService
import org.yaml.snakeyaml.Yaml

import javax.inject.Inject

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
        // Taken from mroPipelineUtil.prepareCheckoutRepoNamedJob

        String projectName = project.key
        String repoName = "${projectName}-${repo.id}"
        String bitbucketUrl = bitbucketService.url
        String bitbucketUrlWithCreds = bitbucketUrlWithUserWithCredentials()
        String bitbucketUrlTail = "/scm/${projectName}/${repoName}.git"

        URI repoUri = new URI("${bitbucketUrl}${bitbucketUrlTail}").normalize()
        URI repoUriWithCreds = new URI("${bitbucketUrlWithCreds}${bitbucketUrlTail}").normalize()
        String repoUrlWithCreds = repoUriWithCreds.toString()

        String releaseBranchName = this.project.gitReleaseBranch
        String targetFolder = tempFolder.root.getAbsolutePath() + "/" + repoName

        String retVal = runCommand("git clone -b ${releaseBranchName} ${repoUrlWithCreds} ${targetFolder}")
        if (retVal == null) {
            releaseBranchName = "master"
            retVal = runCommand("git clone -b master ${repoUriWithCreds} ${targetFolder}")
        }
        if (retVal == null) {
            throw new RuntimeException("Could not clone repository.")
        }

        // in case of a re-checkout, scm.GIT_COMMIT  still points
        // to the old commit.
        def commit = runCommand("git rev-parse HEAD")
        def prevCommit = runCommand("git rev-parse HEAD^1")
        def lastSuccessCommit = prevCommit

        String test = bitbucketService.getBranchCommits(projectName, repoName)
        loggerStub.info(test)

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

    private String bitbucketUrlWithUserWithCredentials() {
        final String HTTPS_HEAD = "https://"
        final String HTTP_HEAD = "http://"

        String bitbucketUrl = bitbucketService.url
        String bitbucketUsername = bitbucketService.getUsername()
        String bitbucketPassword = bitbucketService.getPassword()

        if (bitbucketUsername.contains("@")) {
            bitbucketUsername = bitbucketUsername.replace("@", "%40")
        }

        boolean isHttps = false
        if (bitbucketUrl.startsWith(HTTPS_HEAD)) {
            bitbucketUrl = bitbucketUrl.replace(HTTPS_HEAD, "")
            isHttps = true
        } else {
            bitbucketUrl = bitbucketUrl.replace(HTTP_HEAD, "")
        }

        bitbucketUrl = (isHttps ? HTTPS_HEAD : HTTP_HEAD) +
            "${bitbucketUsername}:${bitbucketPassword}@${bitbucketUrl}"
        return bitbucketUrl
    }

    private def loadDefaultRepoMetadata(repo) {
        repo << [
            metadata: [
                id: repo.id,
                name: repo.name,
                description: "myDescription-A",
                supplier: "mySupplier-A",
                version: "myVersion-A",
                references: "myReferences-A"
            ]
        ]
        return repo
    }

    // a wrapper closure around executing a string
    // can take either a string or a list of strings (for arguments with spaces)
    // prints all output, complains and halts on error
    String runCommand(String cmd) {

        StringBuilder retVal = new StringBuilder()
        loggerStub.info("CMD: ${cmd}" )
        def proc = cmd.execute()
        proc.in.eachLine { String line ->
            loggerStub.info(line)
            retVal.append(line)
        }
        proc.out.close()
        proc.waitFor()

        if (proc.exitValue()) {
            loggerStub.error("gave the following error: ")
            loggerStub.error("[ERROR] ${proc.getErrorStream()}")
        }
        if (proc.exitValue()) {
            return null
        }
        return retVal.toString()
    }
}
