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

    def "git commit with skip pattern: [ci skip]"() {
        given:
        def gitCommitMessage = "docs: update README [ci skip]"
        def script = new PipelineScript()
        def service = new GitService(script, new Logger(script, false))

        when:
        def isSkippingCommitMessage = service.isCiSkipInCommitMessage(gitCommitMessage)

        then:
        isSkippingCommitMessage == true
    }

    def "git commit with skip pattern: [skip ci]"() {
        given:
        def gitCommitMessage = "docs: update README [skip ci]"
        def script = new PipelineScript()
        def service = new GitService(script, new Logger(script, false))

        when:
        def isSkippingCommitMessage = service.isCiSkipInCommitMessage(gitCommitMessage)

        then:
        isSkippingCommitMessage == true
    }

    def "git commit with skip pattern: ***NO_CI***"() {
        given:
        def gitCommitMessage = "docs: update README ***NO_CI***"
        def script = new PipelineScript()
        def service = new GitService(script, new Logger(script, false))

        when:
        def isSkippingCommitMessage = service.isCiSkipInCommitMessage(gitCommitMessage)

        then:
        isSkippingCommitMessage == true
    }

    def "git commit without skip pattern"() {
        given:
        def gitCommitMessage = "docs: update README"
        def script = new PipelineScript()
        def service = new GitService(script, new Logger(script, false))

        when:
        def isSkippingCommitMessage = service.isCiSkipInCommitMessage(gitCommitMessage)

        then:
        isSkippingCommitMessage == false
    }

    def "git commit multiline including skip pattern"() {
        given:
        def gitCommitMessage = """docs: update README

        - typo
        - [ci skip]
        """
        def script = new PipelineScript()
        def service = new GitService(script, new Logger(script, false))

        when:
        def isSkippingCommitMessage = service.isCiSkipInCommitMessage(gitCommitMessage)

        then:
        isSkippingCommitMessage == false
    }
}
