import org.ods.component.IContext
import org.ods.component.UploadToNexusStage

def call(IContext context, Map config = [:]) {
    def stage = new UploadToNexusStage(
        this,
        context,
        config
    )
    return stage.execute()
}
return this
