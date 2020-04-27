package org.ods.component

class UploadToNexusStage extends Stage {
  public final String STAGE_NAME = 'Upload to Nexus'

  UploadToNexusStage(def script, IContext context, Map config) {
    super(script, context, config)
  }

  def run() {
    String repoType = config.repoType ?: "candidates"
    String distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"
    String groupId  = config.groupId ?: context.groupId
    String uploadPath = "${context.nexusHost}/repository/${repoType}/${groupId.replace('.', '/')}/${componentId}/${context.tagversion}/${distFile}"
    
    script.echo ("Uploading ${distFile} to ${uploadPath}")
    script.sh (
      script : "curl -u ${context.nexusUsername}:${context.nexusPassword} --upload-file ${distFile} ${uploadPath}",
      label : "Uploading ${distFile} to Nexus"
    )
    
    return uploadPath
  }
}
