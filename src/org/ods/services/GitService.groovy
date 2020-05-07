package org.ods.services

class GitService {

    private final def script

    GitService(script) {
        this.script = script
    }

    String getOriginUrl() {
        script.sh(
            returnStdout: true,
            script: 'git config --get remote.origin.url',
            label: "Get Git URL of remote 'origin'"
        ).trim()
    }

    String getCommitSha() {
        script.sh(
            returnStdout: true,
            script: 'git rev-parse HEAD',
            label: 'Get Git commit SHA'
        ).trim()
    }

    String getCommitAuthor() {
        script.sh(
            returnStdout: true,
            script: "git --no-pager show -s --format='%an (%ae)' HEAD",
            label: 'Get Git commit author'
        ).trim()
    }

    String getCommitMessage() {
        script.sh(
            returnStdout: true,
            script: 'git log -1 --pretty=%B HEAD',
            label: 'Get Git commit message'
        ).trim()
    }

    String getCommitTime() {
        script.sh(
            returnStdout: true,
            script: 'git show -s --format=%ci HEAD',
            label: 'Get Git commit timestamp'
        ).trim()
    }

    /** Looks in commit message for string '[ci skip]', '[ciskip]', '[ci-skip]' and '[ci_skip]'. */
    boolean isCiSkipInCommitMessage() {
        return script.sh(
            returnStdout: true, script: 'git show --pretty=%s%b -s',
            label: 'check skip CI?'
        ).toLowerCase().replaceAll('[\\s\\-\\_]', '').contains('[ciskip]')
    }

    void checkout(String gitCommit, def userRemoteConfigs) {
        def gitParams = [
            $class: 'GitSCM',
            branches: [[name: gitCommit]],
            doGenerateSubmoduleConfigurations: false,
            submoduleCfg: [],
            userRemoteConfigs: userRemoteConfigs,
        ]
        if (isSlaveNodeGitLfsEnabled()) {
            gitParams.extensions = [
                [$class: 'GitLFSPull']
            ]
        }
        script.checkout(gitParams)
    }

    private boolean isSlaveNodeGitLfsEnabled() {
        def statusCode = script.sh(
            script: 'git lfs &> /dev/null',
            label: 'Check if Git LFS is enabled',
            returnStatus: true
        )
        return statusCode == 0
    }

}
