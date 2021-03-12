package org.ods.services

import org.ods.PipelineScript
import org.ods.util.Logger
import util.SpecHelper

class GitServiceSpec extends SpecHelper {

    def "get release branch"() {
        given:
        def version = "0.0.1"
        def script = new PipelineScript()
        def service = new GitService(script, new Logger(script, false))

        when:
        def releaseBranch = service.getReleaseBranch(version)

        then:
        releaseBranch == "release/0.0.1"
    }

    def "git skipping commit message"() {
        given:
        def script = new PipelineScript()
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
}

