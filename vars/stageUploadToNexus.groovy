import org.ods.component.IContext

def call(IContext context) {
  echo "'uploadToNexus' has been replaced with 'odsComponentStageUploadToNexus', please use that instead."
  odsComponentStageUploadToNexus(
    context
  )
}

return this
