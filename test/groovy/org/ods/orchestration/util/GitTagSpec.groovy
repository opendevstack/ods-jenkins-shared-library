package org.ods.orchestration.util

import spock.lang.*

import static util.FixtureHelper.*
import org.ods.services.GitService

import util.*

class GitTagSpec extends SpecHelper {

    def "serializes to string"() {
        expect:
        new GitTag("1", "A", 0, "D").toString() == "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D"
    }

    def "withNextBuildNumber increases build number"() {
        expect:
        new GitTag("1", "A", 0, "D").withNextBuildNumber().toString() == "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"
    }

    def "withBuildNumber replaces build number"() {
        expect:
        new GitTag("1", "A", 0, "D").withBuildNumber(2).toString() == "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-2-D"
    }

    def "withEnvToken replaces env token"() {
        expect:
        new GitTag("1", "A", 0, "D").withEnvToken('Q').toString() == "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-Q"
    }

    def "reads latest tag of previous environment"() {
        expect:
        GitTag.readLatestBaseTag(tagList, version, changeId, envToken).toString() == result

        where:
        tagList                                                                                     | version | changeId | envToken      || result
        "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D"                                               | "1"     | "A"      | "Q"           || "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D"
        "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D\n${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"  | "1"     | "A"      | "Q"           || "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"
        "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-D\n${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"  | "1"     | "A"      | "D"           || "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-D"
        "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-0-Q\n${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-Q"  | "1"     | "A"      | "P"           || "${GitService.ODS_GIT_TAG_BRANCH_PREFIX}v1-A-1-Q"
        ""                                                                                          | "1"     | "A"      | "P"           || "null"
    }

}
