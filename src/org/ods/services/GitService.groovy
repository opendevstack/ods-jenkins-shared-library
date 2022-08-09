package org.ods.services

import org.ods.util.ILogger

@SuppressWarnings('MethodCount')
class GitService {

    @SuppressWarnings('NonFinalPublicField')
    public static String ODS_GIT_TAG_PREFIX = 'ods-generated-'
    public final static String ODS_GIT_BRANCH_PREFIX = 'release/'

    private final def script
    private final ILogger logger

    GitService(script, logger) {
        this.script = script
        this.logger = logger
    }

    // mergedIssueId gets the issue ID from the merged branch.
    // This only works on merge commits.
    static String mergedIssueId(String project, String repository, String commitMessage) {
        def b = mergedBranch(project, repository, commitMessage)
        if (b) {
            return issueIdFromBranch(b, project)
        }
        ''
    }

    @SuppressWarnings('LineLength')
    static String mergedBranch(String project, String repository, String commitMessage) {
        def uppercaseProject = project.toUpperCase()
        def msgMatcher = commitMessage =~ /(?:Pull request #[0-9]+.*\R+Merge|Merge pull request #[0-9]+) in ${uppercaseProject}\/${repository} from (\S*)|^Merge branch '(\S*)'/
        if (msgMatcher) {
            return msgMatcher[0][1] ?: msgMatcher[0][2]
        }
        ''
    }

    // Looks for an issue ID of form "PROJ-123" in the commit message.
    // If multiple such issue IDs are present, the first one is returned.
    static String issueIdFromCommit(String commitMessage, String projectId) {
        def uppercaseProject = projectId.toUpperCase()
        def msgMatcher = commitMessage =~ /${uppercaseProject}-([0-9]+)/
        if (msgMatcher) {
            return msgMatcher[0][1]
        }
        return ''
    }

    // Looks for an issue ID of form "PROJ-123" in the branch name.
    // If multiple such issue IDs are present, the first one is returned.
    static String issueIdFromBranch(String branchName, String projectId) {
        def tokens = extractBranchCode(branchName).split('-')
        def pId = tokens[0]
        if (!pId || !pId.equalsIgnoreCase(projectId)) {
            return ''
        }
        if (!tokens[1].isNumber()) {
            return ''
        }
        return tokens[1]
    }

    static String extractBranchCode(String branch) {
        if (branch.startsWith('feature/')) {
            def list = branch.drop('feature/'.length()).tokenize('-')
            "${list[0]}-${list[1]}"
        } else if (branch.startsWith('bugfix/')) {
            def list = branch.drop('bugfix/'.length()).tokenize('-')
            "${list[0]}-${list[1]}"
        } else if (branch.startsWith('hotfix/')) {
            def list = branch.drop('hotfix/'.length()).tokenize('-')
            "${list[0]}-${list[1]}"
        } else if (branch.startsWith('release/')) {
            def list = branch.drop('release/'.length()).tokenize('-')
            "${list[0]}-${list[1]}"
        } else {
            branch
        }
    }

    static String getReleaseBranch(String version) {
        "${ODS_GIT_BRANCH_PREFIX}${version}"
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

    String getCommitSubject() {
        script.sh(
            returnStdout: true,
            script: 'git show --pretty=%s -s',
            label: 'Get Git commit subject'
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

    /** Looks in commit message for the following strings
     *  '[ci skip]', '[ciskip]', '[ci-skip]', '[ci_skip]',
     *  '[skip ci]', '[skipci]', '[skip-ci]', '[skip_ci]',
     *  '***NO_CI***', '***NO CI***', '***NOCI***', '***NO-CI***'
     */
    boolean isCiSkipInCommitMessage(String gitCommit = '') {
        def gitCommitSubject = ''
        if (gitCommit) {
            def indexEndOfLine = gitCommit.indexOf('\n')
            gitCommitSubject = gitCommit[0..indexEndOfLine]
        } else {
            gitCommitSubject = getCommitSubject()
        }

        gitCommitSubject = gitCommitSubject.toLowerCase().replaceAll('[\\s\\-\\_]', '')

        return (gitCommitSubject.contains('[ciskip]')
                 || gitCommitSubject.contains('[skipci]')
                 || gitCommitSubject.contains('***noci***'))
    }
    void checkout(
        String branch,
        def extensions,
        def userRemoteConfigs,
        boolean doGenerateSubmoduleConfigurations = false) {
        def branches = [[name: branch]]
        this.checkout(
            branches,
            extensions,
            userRemoteConfigs,
            doGenerateSubmoduleConfigurations
        )
        }
    void checkout(
        def branches,
        def extensions,
        def userRemoteConfigs,
        boolean doGenerateSubmoduleConfigurations = false) {
        def gitParams = [
            $class: 'GitSCM',
            branches: branches,
            doGenerateSubmoduleConfigurations: doGenerateSubmoduleConfigurations,
            extensions: [[
                    $class: 'SubmoduleOption',
                    disableSubmodules: false,
                    parentCredentials: true,
                    recursiveSubmodules: true,
                    reference: '',
                    trackingSubmodules: false],
                    [$class: 'CleanBeforeCheckout'],
                    [$class: 'CleanCheckout']
                    ],
            submoduleCfg: [],
            userRemoteConfigs: userRemoteConfigs,
        ]
        if (!extensions.empty) {
            gitParams.extensions += extensions
        }
        if (isAgentNodeGitLfsEnabled()) {
            gitParams.extensions << [$class: 'GitLFSPull']
        }
        script.checkout(gitParams)
    }

    def configureUser() {
        script.sh(
            script: """
                git config --global user.email 'undefined'
                git config --global user.name 'ODS Jenkins Shared Library System User'
                """,
            label: 'configure git system user'
        )
    }

    def commit(List files, String msg, boolean allowEmpty = true) {
        def allowEmptyFlag = allowEmpty ? '--allow-empty' : ''
        def filesToAddCommand = "git add ${files.join(' ')}"
        if (files.empty) {
            filesToAddCommand = ''
        }
        script.sh(
            script: """
                ${filesToAddCommand}
                git commit -m "${msg}" ${allowEmptyFlag}
            """,
            label: 'Commit'
        )
    }

    def createTag(String name) {
        script.sh(
            script: """git tag -f -a -m "${name}" ${name}""",
            label: "tag with ${name}"
        )
    }

    def pushRef(String name) {
        script.sh(
            script: "git push origin ${name}",
            label: "Push ref ${name}"
        )
    }

    def pushBranchWithTags(String name) {
        script.sh(
            script: "git push --tags origin ${name}",
            label: "Push branch ${name} with tags"
        )
    }

    def pushForceBranchWithTags(String name) {
        script.sh(
            script: "git push -f --tags origin ${name}",
            label: "Push branch ${name} with tags and force"
        )
    }

    boolean remoteTagExists(String name) {
        def tagStatus = script.sh(
            script: "git ls-remote --exit-code --tags origin ${name} &>/dev/null",
            label: "check if tag ${name} exists",
            returnStatus: true
        )
        tagStatus == 0
    }

    boolean localTagExists(String name) {
        def tagStatus = script.sh(
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
        def branchCheckStatus = script.sh(
            script: """git show-ref --verify --quiet ${name}""",
            returnStatus: true,
            label: "Check if ${name} already exists"
        )
        return branchCheckStatus == 0
    }

    // isAncestor returns true if maybeAncestorCommit is an ancestor of
    // descendantCommit, or, in other words, if descendantCommit contains
    // everything of maybeAncestorCommit (and potentially more).
    boolean isAncestor(String maybeAncestorCommit, String descendantCommit) {
        script.sh(
            script: "git merge-base --is-ancestor ${maybeAncestorCommit} ${descendantCommit}",
            returnStatus: true,
            label: "Check if ${descendantCommit} is descendant of ${maybeAncestorCommit}"
        ) == 0
    }

    void switchToRemoteBranch(String branch) {
        script.sh(
            script: "git checkout --track origin/${branch}",
            label: "Checkout branch origin/${branch}"
        )
    }

    void switchToExistingBranch(String branch) {
        script.sh(
            script: "git checkout ${branch}",
            label: "Checkout branch ${branch}"
        )
    }

    void switchToOriginTrackingBranch(String branch) {
        if (localBranchExists(branch)) {
            script.sh(
                script: "git branch -D ${branch}",
                label: "Delete local ${branch} branch"
            )
        }
        script.sh(
            script: "git checkout -b ${branch} origin/${branch}",
            label: "Checkout new branch based on origin/${branch}"
        )
    }

    void checkoutAndCommitFiles(String branchToCheckoutFrom, List<String> filesToCheckout, String msg) {
        script.sh("git checkout ${branchToCheckoutFrom} -- ${filesToCheckout.join(' ')}")
        commit(filesToCheckout, msg)
    }

    void mergeIntoMainBranch(String branchToMerge, String mainBranch, List<String> filesToCheckout) {
        switchToOriginTrackingBranch(mainBranch)
        checkoutAndCommitFiles(branchToMerge, filesToCheckout, "ODS RM merge from ${branchToMerge} [ci skip]")
        script.sh("""
            git merge ${branchToMerge} --no-edit -m "ODS RM branch ${branchToMerge} into ${mainBranch} [ci skip]"
        """)
        pushRef(mainBranch)
        script.sh("git checkout ${branchToMerge}")
    }

    def checkoutNewLocalBranch(String name) {
        // Local state might have a branch from previous, failed pipeline runs.
        // If so, we'd rather start from a clean state.
        if (localBranchExists(name)) {
            script.sh(
                script: "git branch -D ${name}",
                label: "delete local ${name} branch"
            )
        }
        script.sh(
            script: "git checkout -b ${name}",
            label: "create new ${name} branch"
        )
    }

    String readBaseTagList(String version, String changeId, String envToken) {
        def previousEnvToken = 'D'
        if (envToken == 'P') {
            previousEnvToken = 'Q'
        }
        def tagPattern = "${ODS_GIT_TAG_PREFIX}v${version}-${changeId}-*-${previousEnvToken}"
        script.sh(
            script: "git tag --list '${tagPattern}'",
            returnStdout: true,
            label: "list tags for version ${version}-${changeId}-*-${previousEnvToken}"
        ).trim()
    }

    private boolean isAgentNodeGitLfsEnabled() {
        def statusCode = script.sh(
            script: 'git lfs &> /dev/null',
            label: 'Check if Git LFS is enabled',
            returnStatus: true
        )
        return statusCode == 0
    }

}
