package org.ods.orchestration.util

class GitUtil {

    private GitUtil() { // This is a utility class
    }

    static String buildGitBranchUrl(String gitRepoUrl, String projectKey, String repoName, String gitBranch) {
        if (gitRepoUrl == null) {
            return null
        }
        String gitBaseUrl = gitRepoUrl[0..gitRepoUrl.indexOf("/scm/")-1]
        return "${gitBaseUrl}/projects/${projectKey}/repos/${repoName}/browse?at=refs%2Fheads%2F${gitBranch}"
    }

}
