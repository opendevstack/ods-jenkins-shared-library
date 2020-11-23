package org.ods.orchestration.util

import org.ods.services.GitService

class GitTag {

    // Major version, e.g. 1, 2, 3, etc.
    private final String version
    // Change ID (referring to an external change tracking system)
    private final String changeId
    // Iteration is a number automatically increased by the orchestration pipeline.
    // This is part of the Git tag as the Jenkins build number alone is not reliable
    // enough (it might reset when the persistent volume is deleted).
    private final int iteration
    // Build number is the Jenkins build number. This is part of the Git tag as
    // the iteration alone is not reliable enough (errors during the pipeline
    // run might prevent the next assembly). Note that the build number
    // referenced here is always the build number of the assembling pipeline run.
    private final String buildNumber
    // envToken signifies the environment which was targeted by the
    // orchestration pipeline. One of D, Q or P.
    private final String envToken

    GitTag(String version, String changeId, int iteration, String buildNumber, String envToken) {
        this.version = version
        this.changeId = changeId
        this.iteration = iteration
        this.buildNumber = buildNumber
        this.envToken = envToken
    }

    static GitTag readLatestBaseTag(String tagList, String version, String changeId, String envToken) {
        def previousEnvToken = 'D'
        if (envToken == 'P') {
            previousEnvToken = 'Q'
        }
        if (tagList) {
            def tags = tagList.split("\n")

            def highestIteration = -1
            def highestBuildNumber = '0'
            tags.each {
                def iterationInfo = extractIterationInfo(it, version, changeId, previousEnvToken)
                if (iterationInfo) {
                    def iterationCandidate = iterationInfo.first().toInteger()
                    def buildNumberCandidate = iterationInfo.last()
                    if (iterationCandidate > highestIteration) {
                        highestIteration = iterationCandidate
                        highestBuildNumber = buildNumberCandidate
                    }
                }
            }
            if (highestIteration > -1 ) {
                return new GitTag(version, changeId, highestIteration, highestBuildNumber, previousEnvToken)
            }
        }
        return null
    }

    static List<String> extractIterationInfo(String tag, String version, String changeId, String envToken) {
        List<String> iterationParts = []
        if (tag && tag.contains('-') && tag.size() > 4) {
            iterationParts = tag
                .replace("${GitService.ODS_GIT_TAG_PREFIX}v${version}-${changeId}-", '')
                .replace("-${envToken}", '')
                .split('b')
        }
        if (iterationParts.size() == 1) {
            iterationParts.push('0')
        }
        iterationParts
    }

    String toString() {
        "${GitService.ODS_GIT_TAG_PREFIX}v${version}-${changeId}-${iteration}b${buildNumber}-${envToken}"
    }

    GitTag nextIterationWithBuildNumber(String buildNumber) {
        withIterationAndBuildNumber(this.iteration + 1, buildNumber)
    }

    GitTag withIterationAndBuildNumber(int iteration, String buildNumber) {
        new GitTag(this.version, this.changeId, iteration, buildNumber, this.envToken)
    }

    GitTag withEnvToken(String envToken) {
        new GitTag(this.version, this.changeId, this.iteration, this.buildNumber, envToken)
    }

}
