package org.ods.component

class UploadToNexusStage extends Stage {
  public final String STAGE_NAME = 'Upload to Nexus'

  def repoType
  def distFile
  def groupId
  
  UploadToNexusStage(def script, IContext context, Map config) {
    super(script, context, config)
    repoType = config.repoType ?: "candidates"
    distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"
    groupId  = config.groupId ?: context.groupId
  }

  def run() {
    def uploadPath = "${context.nexusHost}/repository/${repoType}/${groupId.replace('.', '/')}/${componentId}/${context.tagversion}/${distFile}"
    script.echo ("Uploading ${distFile} to ${uploadPath}")
    script.sh (
      script : "curl -u ${context.nexusUsername}:${context.nexusPassword} --upload-file ${distFile} ${uploadPath}",
      label : "Uploading ${distFile} to Nexus"
    )
    
    return uploadPath
  }
}
