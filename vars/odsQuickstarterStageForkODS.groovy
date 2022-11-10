import org.ods.quickstarter.ForkFromGithubODSStage
import org.ods.quickstarter.IContext

def call(IContext context, Map config = [:]) {
    def stage = new ForkFromGithubODSStage(this, context, config)
    stage.execute()
}
return this

