package org.ods.orchestration.util

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class GitTagSpec extends SpecHelper {

    def "serializes to string"() {
        expect:
        new GitTag("1", "A", 0, "D").toString() == "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D"
    }

    def "withNextBuildNumber increases build number"() {
        expect:
        new GitTag("1", "A", 0, "D").withNextBuildNumber().toString() == "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"
    }

    def "withBuildNumber replaces build number"() {
        expect:
        new GitTag("1", "A", 0, "D").withBuildNumber(2).toString() == "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-2-D"
    }

    def "withEnvToken replaces env token"() {
        expect:
        new GitTag("1", "A", 0, "D").withEnvToken('Q').toString() == "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-Q"
    }

    def "reads latest tag of previous environment"() {
        expect:
        GitTag.readLatestBaseTag(tagList, version, changeId, envToken).toString() == result

        where:
        tagList                                                                                     | version | changeId | envToken      || result
        "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D"                                               | "1"     | "A"      | "Q"           || "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D"
        "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D\n${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"  | "1"     | "A"      | "Q"           || "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"
        "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D\n${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"  | "1"     | "A"      | "D"           || "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"
        "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-Q\n${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-Q"  | "1"     | "A"      | "P"           || "${GitTag.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-Q"
        ""                                                                                          | "1"     | "A"      | "P"           || "null"
    }

}
