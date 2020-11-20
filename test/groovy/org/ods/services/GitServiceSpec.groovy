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
}

