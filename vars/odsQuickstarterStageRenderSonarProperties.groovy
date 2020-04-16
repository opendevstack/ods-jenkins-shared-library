import org.ods.quickstarter.RenderSonarPropertiesStage
import org.ods.quickstarter.IContext

def call(IContext context, Map config = [:]) {
    def stage = new RenderSonarPropertiesStage(this, context, config)
    stage.execute()
}
return this
