package org.ods.services

import util.PipelineSteps
import org.ods.util.Logger
import util.SpecHelper

class GitServiceSpec extends SpecHelper {

    def "get release branch"() {
        given:
        def version = "0.0.1"
        def script = new PipelineSteps()
        def service = new GitService(script, new Logger(script, false))

        when:
        def releaseBranch = service.getReleaseBranch(version)

        then:
        releaseBranch == "release/0.0.1"
    }

    def "git skipping commit message"() {
        given:
        def script = new PipelineSteps()
        def service = new GitService(script, new Logger(script, false))

        when:
        def result = service.isCiSkipInCommitMessage(gitCommitMessage)

        then:
        result == isSkippingCommitMessage

        where:
        gitCommitMessage                             || isSkippingCommitMessage
        'docs: update README [ci skip]'              || true
        'docs: update README [skip ci]'              || true
        'docs: update README ***NO_CI***'            || true
        'docs: update README'                        || false
        'docs: update README\n\n- typo\n- [ci skip]' || false
    }

    def "merged branch"() {
        given:
        def script = new PipelineSteps()
        def service = new GitService(script, new Logger(script, false))

        when:
        def result = service.mergedBranch("odm", "odm-components", gitCommitMessage)

        then:
        result == mergedBranch

        where:
        gitCommitMessage                                                                            || mergedBranch
        'Merge pull request #62 in ODM/odm-components from bugfix/ODM-509-foo to master'            || 'bugfix/ODM-509-foo'
        'Pull request #62: test\n\nMerge in ODM/odm-components from bugfix/ODM-509-foo to master'   || 'bugfix/ODM-509-foo'
        "Merge branch 'bugfix/ODM-509-foo' to master"                                               || 'bugfix/ODM-509-foo'
    }
}

