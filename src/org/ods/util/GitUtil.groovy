package org.ods.util

import org.ods.util.IPipelineSteps

class GitUtil {

    private IPipelineSteps steps

    GitUtil(IPipelineSteps steps) {
        this.steps = steps
    }

    private String execute(String cmd) {
        return this.steps.sh(returnStdout: true, script: cmd).trim()
    }

    String getCommit() {
        return this.execute("git rev-parse HEAD")
    }

    String getURL() {
        return this.execute("git config --get remote.origin.url")
    }

    def configureUser() {
        steps.sh(
            script: """
            git config --global user.email "undefined"
            git config --global user.name "System User"
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

    def checkout(String gitRef, def extensions, def userRemoteConfigs, boolean doGenerateSubmoduleConfigurations = false) {
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
        steps.sh(
            script: "git tag --list 'v${version}-${changeId}-[0-9]*-${previousEnvToken}'",
            returnStdout: true,
            label: "list tags for version ${version}-${changeId}-*-${previousEnvToken}"
        ).trim()
    }

    static String getReleaseBranch(String version) {
        "release/${version}"
    }
}
