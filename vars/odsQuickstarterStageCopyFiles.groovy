import org.ods.quickstarter.CopyFilesStage
import org.ods.quickstarter.IContext

def call(IContext context, Map config = [:]) {
    def stage = new CopyFilesStage(this, context, config)
    stage.execute()
}
return this
