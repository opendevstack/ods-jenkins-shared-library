package org.ods.quickstarter

import org.ods.PipelineScript

import util.SpecHelper


class PushToRemoteStageSpec extends SpecHelper {
    PushToRemoteStage pushToRemoteStage
    IContext context
    PipelineScript script

    def setup() {
        script = Spy(new PipelineScript())
        context = new Context([targetDir: 'fake-dir', cdUserCredentialsId: 'credentials-id', bitbucketUrl: 'http://fake-url'])
    }

    def "successful execution without git submodules"() {
        given:
            pushToRemoteStage = Spy(new PushToRemoteStage(script, context, [:]))

        when:
            pushToRemoteStage.run()

        then:
            1 * script.fileExists(_) >> false
            1 * script.echo("Initializing quickstarter git repo ${context.targetDir} @${context.gitUrlHttp}")
            1 * script.sh({ it.label == 'Copy quickstarter files' })
            0 * script.sh({ it.label == 'Add submodule to quickstarter files' })
            1 * script.sh({ it.label == 'Commit quickstarter files' })
            1 * script.echo("Pushing quickstarter git repo to ${context.gitUrlHttp}")
            1 * script.sh({ it.label == 'Push to remote' })
    }

    def "successful execution with git submodules"() {
        given:
            pushToRemoteStage = Spy(new PushToRemoteStage(script, context, [
                gitSubModules: [
                    [name: 'submodule', url: 'https://fake-submodule.git', branch: 'master', folder: 'src/code']
                ],
            ]))

        when:
            pushToRemoteStage.run()

        then:
            1 * script.fileExists(_) >> false
            1 * script.echo("Initializing quickstarter git repo ${context.targetDir} @${context.gitUrlHttp}")
            1 * script.sh({ it.label == 'Copy quickstarter files' })
            1 * script.sh({ it.label == 'Add submodule to quickstarter files' })
            1 * script.sh({ it.label == 'Commit quickstarter files' })
            1 * script.echo("Pushing quickstarter git repo to ${context.gitUrlHttp}")
            1 * script.sh({ it.label == 'Push to remote' })
    }
}
