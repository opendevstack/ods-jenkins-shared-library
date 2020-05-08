package org.ods.component

import org.ods.services.NexusService

class UploadToNexusStage extends Stage {

    public final String STAGE_NAME = 'Upload to Nexus'

    final String repository
    final String repositoryType
    final String distFile
    final String groupId
    final String artifactId
    final String version
    final NexusService nexus
    
    UploadToNexusStage(def script, IContext context, Map config) {
        super(script, context, config)
        this.repository = config.repository ?: 'candidates'
        this.repositoryType = config.repositoryType ?: 'maven2'
        this.distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"
        this.groupId  = config.groupId ?: context.groupId.replace('.', '/')
        this.artifactId = config.artifactId ?: componentId
        this.version = config.version ?: context.tagversion

        nexus = new NexusService(
            context.nexusHost, context.nexusUsername, context.nexusPassword)
    }

    def run() {
        if (!script.fileExists (distFile)) {
            script.error ("Could not upload file ${distFile} - it does NOT exist!")
            return
        }

        def fileExtension = distFile.substring(distFile.lastIndexOf('.') + 1)
        Map nexusParams = [ : ]
        
        if (repositoryType == 'maven2') {
            nexusParams << ['maven2.groupId' : this.groupId]
            nexusParams << ['maven2.artifactId' : this.artifactId]
            nexusParams << ['maven2.version' : this.version]
            nexusParams << ['maven2.asset1.extension' : fileExtension]
        } 
        script.echo ("Nexus upload params: ${nexusParams}, file: ${distFile}, extension: ${fileExtension}")
        def uploadUri = nexus.storeComplextArtifact(
            repository,
            script.readFile(['file' : distFile, 'encoding' : 'Base64']).getBytes(),
            'application/octet-stream', 
            repositoryType,
            nexusParams)
        script.echo "Uploaded '${distFile}' to '${uploadUri}'"
        return uploadUri
    }

}
