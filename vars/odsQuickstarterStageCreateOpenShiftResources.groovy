import org.ods.quickstarter.CreateOpenShiftResourcesStage
import org.ods.quickstarter.IContext

def call(IContext context, Map config = [:]) {
    def stage = new CreateOpenShiftResourcesStage(this, context, config)
    stage.execute()
}
return this
