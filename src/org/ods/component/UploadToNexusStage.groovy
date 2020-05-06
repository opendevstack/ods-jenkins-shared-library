package org.ods.component

class UploadToNexusStage extends Stage {

    public final String STAGE_NAME = 'Upload to Nexus'

    def repoType
    def distFile
    def groupId
    def uploadPath
    
    UploadToNexusStage(def script, IContext context, Map config) {
        super(script, context, config)
        this.repoType = config.repoType ?: 'candidates'
        this.distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"
        this.groupId  = config.groupId ?: context.groupId
        def repo = "${context.nexusHost}/repository/${repoType}"
        this.uploadPath = "${repo}/${groupId.replace('.', '/')}/${componentId}/${context.tagversion}/${distFile}"
    }

    def run() {
        script.echo ("Uploading '${distFile}' to: ${uploadPath}")

        if (!script.fileExists (distFile)) {
            script.error ("Could not upload file ${distFile} - it does NOT exist!")
        }

        def user = "${context.nexusUsername}:${context.nexusPassword}"
        script.sh (
            script: "curl -u ${user} --upload-file ${distFile} ${uploadPath}",
            label: "Uploading ${distFile} to Nexus"
        )
        return uploadPath
    }

}
