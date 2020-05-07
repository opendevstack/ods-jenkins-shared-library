package org.ods.component

import org.ods.services.NexusService

class UploadToNexusStage extends Stage {

    public final String STAGE_NAME = 'Upload to Nexus'

    final def repoType
    final def distFile
    final def uploadPath
    final NexusService nexus
    final def path

    UploadToNexusStage(def script, IContext context, Map config) {
        super(script, context, config)
        this.repoType = config.repoType ?: 'candidates'
        this.distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"
        def groupId  = config.groupId ?: context.groupId
        this.uploadPath = "${groupId.replace('.', '/')}/${componentId}/${context.tagversion}"
        this.path = config.path ?: script.env.WORKSPACE

        nexus = new NexusService(context.nexusHost, context.nexusUsername, context.nexusPassword)
    }

    def run() {
        script.echo ("Uploading '${distFile}' to: ${uploadPath}")

        if (!script.fileExists ("${path}/${distFile}")) {
            script.error ("Could not upload file '${path}/${distFile}' - it does NOT exist!")
        }

        return nexus.storeArtifactFromFile(repoType, uploadPath, distFile, 
            new File("${path}/${distFile}"), "application/octet-stream")
    }

}
