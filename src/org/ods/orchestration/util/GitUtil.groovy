package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class GitUtil {

    private GitUtil() { // This is a utility class
    }

    static String buildGitBranchUrl(String gitRepoUrl, String projectKey, String repoName, String gitBranch) {
        if (gitRepoUrl == null) {
            return null
        }
        String gitBaseUrl = gitRepoUrl.substring(0, gitRepoUrl.indexOf("/scm/"))
        return "${gitBaseUrl}/projects/${projectKey}/repos/${repoName}/browse?at=refs%2Fheads%2F${gitBranch}"
    }
}
