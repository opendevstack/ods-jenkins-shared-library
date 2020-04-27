package org.ods.component

class UploadToNexusStage extends Stage {
  public final String STAGE_NAME = 'Upload to Nexus'

  UploadToNexusStage(def script, IContext context, Map config) {
    super(script, context, config)
  }

  def run() {
    script.echo "repo"
    def repoType = config.repoType ?: "candidates"
    script.echo "repo"
    def distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"
    script.echo "distf"
    def groupId  = config.groupId ?: context.groupId
    script.echo "group"
    
    def uploadPath = "${context.nexusHost}/repository/${repoType}/${groupId.replace('.', '/')}/${componentId}/${context.tagversion}/${distFile}"
    script.echo "path"
    
    script.echo ("Uploading ${distFile} to ${uploadPath}")
    script.sh (
      script : "curl -u ${context.nexusUsername}:${context.nexusPassword} --upload-file ${distFile} ${uploadPath}",
      label : "Uploading ${distFile} to Nexus"
    )
    
    return uploadPath
  }
}
