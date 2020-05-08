package org.ods.component

import org.ods.services.NexusService

class UploadToNexusStage extends Stage {

    public final String STAGE_NAME = 'Upload to Nexus'

    final String repository
    final String distFile
    final String groupId
    final String artifactId
    final String version
    final NexusService nexus
    
    UploadToNexusStage(def script, IContext context, Map config) {
        super(script, context, config)
        this.repository = config.repository ?: 'candidates'
        this.distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"
        this.groupId  = config.groupId ?: context.groupId.replace('.', '/')
        this.artifactId = config.componentId ?: componentId
        this.version = config.version ?: context.tagversion

        nexus = new NexusService(
            context.nexusHost, 
            context.nexusUsername, 
            context.nexusPassword)
    }

    def run() {
        if (!script.fileExists (distFile)) {
            script.error ("Could not upload file ${distFile} - it does NOT exist!")
            return
        }

        def fileExtension = distFile.substring(distFile.lastIndexOf('.'))
        Map nexusParams = [
            'groupId' : this.groupId,
            'artifactId' : this.artifactId,
            'version' : this.version,
            'extension' : fileExtension,
        ]
        def uploadUri = nexus.storeComplextArtifact(
            repository,
            script.readFile(['file' : distFile, 'encoding' : 'Base64']).getBytes(),
            'application/octet-stream',
            nexusParams)
        script.echo "Uploaded '${distFile}' to '${uploadUri}'"
        return uploadUri
    }

}
