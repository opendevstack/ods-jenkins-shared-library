import org.ods.component.IContext
import org.ods.component.UploadToNexusStage

// https://help.sonatype.com/repomanager3/rest-and-integration-api/components-api#ComponentsAPI-UploadComponent
def call(IContext context, Map config = [:]) {
    def stage = new UploadToNexusStage(
        this,
        context,
        config
    )
    return stage.execute()
}
return this
