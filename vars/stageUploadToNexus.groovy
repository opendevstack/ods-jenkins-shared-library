import org.ods.component.IContext

def call(IContext context) {
    echo "'stageUploadToNexus' is deprecated, please use " +
        "'odsComponentStageUploadToNexus' instead."
    odsComponentStageUploadToNexus(context)
}

return this
