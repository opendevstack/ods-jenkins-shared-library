package org.ods.orchestration.util

import org.ods.orchestration.util.IPipelineSteps
import org.ods.orchestration.util.GitTag

class GitService {

    private IPipelineSteps steps

    @SuppressWarnings('NonFinalPublicField')
    public static String ODS_GIT_TAG_BRANCH_PREFIX = "ods-generated-"

    GitService(IPipelineSteps steps) {
        this.steps = steps
    }

    String getCommitSha() {
        return this.steps.sh(
            script: "git rev-parse HEAD",
            returnStdout: true,
            label: "get Git commit"
        ).trim()
    }

    String getOriginUrl() {
        return this.steps.sh(
            script: "git config --get remote.origin.url",
            returnStdout: true,
            label: "get Git remote URL"
        ).trim()
    }

    String getCommitAuthor() {
        return this.steps.sh(
            returnStdout: true,
            script: "git --no-pager show -s --format='%an (%ae)' HEAD",
            label: 'Get Git commit author'
        ).trim()
    }

    String getCommitMessage() {
        return this.steps.sh(
            returnStdout: true,
            script: "git log -1 --pretty=%B HEAD",
            label: 'Get Git commit message'
        ).trim()
    }

    String getCommitTime() {
        return this.steps.sh(
            returnStdout: true,
            script: "git show -s --format=%ci HEAD",
            label: 'Get Git commit timestamp'
        ).trim()
    }

    def configureUser() {
        steps.sh(
            script: """
            git config --global user.email "undefined"
            git config --global user.name "MRO System User"
            """,
            label: "configure git system user"
        )
    }

    def createTag(String name) {
        steps.sh(
            script: """git tag -a -m "${name}" ${name}""",
            label: "tag with ${name}"
        )
    }

    def pushTag(String name) {
        steps.sh(
            script: "git push origin ${name}",
            label: "push tag ${name}"
        )
    }

    def pushBranchWithTags(String name) {
        steps.sh(
            script: "git push --tags origin ${name}",
            label: "push branch ${name} with tags"
        )
    }

    def checkout(
        String gitRef,
        def extensions,
        def userRemoteConfigs,
        boolean doGenerateSubmoduleConfigurations = false) {
        steps.checkout([
            $class: 'GitSCM',
            branches: [[name: gitRef]],
            doGenerateSubmoduleConfigurations: doGenerateSubmoduleConfigurations,
            extensions: extensions,
            userRemoteConfigs: userRemoteConfigs
        ])
    }

    boolean remoteTagExists(String name) {
        def tagStatus = steps.sh(
            script: "git ls-remote --exit-code --tags origin ${name} &>/dev/null",
            label: "check if tag ${name} exists",
            returnStatus: true
        )
        tagStatus == 0
    }

    boolean localTagExists(String name) {
        def tagStatus = steps.sh(
            script: "git rev-parse ${name} &>/dev/null",
            label: "check if tag ${name} exists",
            returnStatus: true
        )
        tagStatus == 0
    }

    boolean localBranchExists(String name) {
        branchExists("refs/heads/${name}")
    }

    boolean remoteBranchExists(String name) {
        branchExists("refs/remotes/origin/${name}")
    }

    boolean branchExists(String name) {
        def branchCheckStatus = steps.sh(
            script: """git show-ref --verify --quiet ${name}""",
            returnStatus: true,
            label: "Check if ${name} already exists"
        )
        return branchCheckStatus == 0
    }

    def checkoutNewLocalBranch(String name) {
        // Local state might have a branch from previous, failed pipeline runs.
        // If so, we'd rather start from a clean state.
        if (localBranchExists(name)) {
            steps.sh(
                script: "git branch -D ${name}",
                label: "delete local ${name} branch"
            )
        }
        steps.sh(
            script: "git checkout -b ${name}",
            label: "create new ${name} branch"
        )
    }

    String readBaseTagList(String version, String changeId, String envToken) {
        def previousEnvToken = 'D'
        if (envToken == 'P') {
            previousEnvToken = 'Q'
        }
        def tagPattern = "${ODS_GIT_TAG_BRANCH_PREFIX}v${version}-${changeId}-[0-9]*-${previousEnvToken}"
        steps.sh(
            script: "git tag --list '${tagPattern}'",
            returnStdout: true,
            label: "list tags for version ${version}-${changeId}-*-${previousEnvToken}"
        ).trim()
    }

}
