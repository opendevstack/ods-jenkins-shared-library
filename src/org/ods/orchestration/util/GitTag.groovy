package org.ods.orchestration.util

class GitTag {

    private String version
    private String changeId
    private int buildNumber
    private String envToken

    @SuppressWarnings('NonFinalPublicField')
    public static String ODS_GIT_TAG_BRANCH_PREFIX = "ods-generated-"

    GitTag(String version, String changeId, int buildNumber, String envToken) {
        this.version = version
        this.changeId = changeId
        this.buildNumber = buildNumber
        this.envToken = envToken
    }

    String toString() {
        "${ODS_GIT_TAG_BRANCH_PREFIX}v${version}-${changeId}-${buildNumber}-${envToken}"
    }

    GitTag withNextBuildNumber() {
        withBuildNumber(this.buildNumber + 1)
    }

    GitTag withBuildNumber(int buildNumber) {
        new GitTag(this.version, this.changeId, buildNumber, this.envToken)
    }

    GitTag withEnvToken(String envToken) {
        new GitTag(this.version, this.changeId, this.buildNumber, envToken)
    }

    static GitTag readLatestBaseTag(String tagList, String version, String changeId, String envToken) {
        def previousEnvToken = 'D'
        if (envToken == 'P') {
            previousEnvToken = 'Q'
        }
        def highestBuildNumber = -1
        if (tagList) {
            def buildNumbers = tagList.split("\n").collect {
                extractBuildNumber(it, version, changeId, previousEnvToken)
            }.sort()
            highestBuildNumber = buildNumbers.last()
            return new GitTag(version, changeId, highestBuildNumber, previousEnvToken)
        }
        return null
    }

    static int extractBuildNumber(String tag, String version, String changeId, String envToken) {
        def buildNumber = -1
        if (tag && tag.contains('-') && tag.size() > 4) {
            buildNumber = tag
                .replace("${ODS_GIT_TAG_BRANCH_PREFIX}v${version}-${changeId}-", '')
                .replace("-${envToken}", '')
                .toInteger()
        }
        buildNumber
    }

}
