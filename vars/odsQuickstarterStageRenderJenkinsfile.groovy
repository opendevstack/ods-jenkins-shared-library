import org.ods.quickstarter.RenderJenkinsfileStage
import org.ods.quickstarter.IContext

def call(IContext context, Map config = [:]) {
    def stage = new RenderJenkinsfileStage(this, context, config)
    stage.execute()
}
return this
