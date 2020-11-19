package org.ods.orchestration.util

import spock.lang.*

import static util.FixtureHelper.*
import org.ods.services.GitService

import util.*

class GitTagSpec extends SpecHelper {

    def "serializes to string"() {
        expect:
        new GitTag("1", "A", 0, "156", "D").toString() == "${GitService.ODS_GIT_TAG_PREFIX}v1-A-0b156-D"
    }

    def "nextIterationWithBuildNumber increases iteration and replaces build number"() {
        expect:
        new GitTag("1", "A", 0, "156", "D").nextIterationWithBuildNumber("157").toString() == "${GitService.ODS_GIT_TAG_PREFIX}v1-A-1b157-D"
    }

    def "withIterationAndBuildNumber replaces iteration and build number"() {
        expect:
        new GitTag("1", "A", 0, "156", "D").withIterationAndBuildNumber(2, "157").toString() == "${GitService.ODS_GIT_TAG_PREFIX}v1-A-2b157-D"
    }

    def "withEnvToken replaces env token"() {
        expect:
        new GitTag("1", "A", 0, "156", "D").withEnvToken('Q').toString() == "${GitService.ODS_GIT_TAG_PREFIX}v1-A-0b156-Q"
    }

    def "reads latest tag of previous environment"() {
        expect:
        GitTag.readLatestBaseTag(tagList, version, changeId, envToken).toString() == result

        where:
        tagList                                                                                               | version | changeId | envToken      || result
        "${GitService.ODS_GIT_TAG_PREFIX}v1-A-0-D"                                                            | "1"     | "A"      | "Q"           || "${GitService.ODS_GIT_TAG_PREFIX}v1-A-0b0-D"
        "${GitService.ODS_GIT_TAG_PREFIX}v1-A-0-D\n${GitService.ODS_GIT_TAG_PREFIX}v1-A-1-D"                  | "1"     | "A"      | "Q"           || "${GitService.ODS_GIT_TAG_PREFIX}v1-A-1b0-D"
        "${GitService.ODS_GIT_TAG_PREFIX}v1-A-0-D\n${GitService.ODS_GIT_TAG_PREFIX}v1-A-1-D"                  | "1"     | "A"      | "D"           || "${GitService.ODS_GIT_TAG_PREFIX}v1-A-1b0-D"
        "${GitService.ODS_GIT_TAG_PREFIX}v1-A-0-Q\n${GitService.ODS_GIT_TAG_PREFIX}v1-A-1-Q"                  | "1"     | "A"      | "P"           || "${GitService.ODS_GIT_TAG_PREFIX}v1-A-1b0-Q"
        "${GitService.ODS_GIT_TAG_PREFIX}v1-A-0-D\n${GitService.ODS_GIT_TAG_PREFIX}v1-A-1b99-D"               | "1"     | "A"      | "Q"           || "${GitService.ODS_GIT_TAG_PREFIX}v1-A-1b99-D"
        ""                                                                                                    | "1"     | "A"      | "P"           || "null"
    }

}
