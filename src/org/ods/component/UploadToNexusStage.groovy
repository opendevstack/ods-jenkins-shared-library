package org.ods.component

import org.ods.services.NexusService

class UploadToNexusStage extends Stage {

    public final String STAGE_NAME = 'Upload to Nexus'

    final String repository
    final String repositoryType
    final String distFile
    final NexusService nexus
    
    UploadToNexusStage(def script, IContext context, Map config) {
        super(script, context, config)
        this.repository = config.repository ?: 'candidates'
        this.repositoryType = config.repositoryType ?: 'maven2'
        this.distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"

        nexus = new NexusService(
            context.nexusHost, context.nexusUsername, context.nexusPassword)
    }

    def run() {
        if (!script.fileExists (distFile)) {
            script.error ("Could not upload file ${distFile} - it does NOT exist!")
            return
        }

        String fileAbsolutePath = script.sh(
            script: "find ~+ -type f -name ${distFile}",
            returnStdout: true,
            label: "find file '${distFile}'"
        ).trim()
        
        Map nexusParams = [ : ]
        
        if (repositoryType == 'maven2') {
            nexusParams << ['maven2.groupId' : config.groupId ?: context.groupId.replace('.', '/')]
            nexusParams << ['maven2.artifactId' : config.artifactId ?: componentId]
            nexusParams << ['maven2.version' : config.version ?: context.tagversion]
            nexusParams << ['maven2.asset1.extension' : distFile.substring(distFile.lastIndexOf('.') + 1)]
        }

        script.echo ("Nexus upload params: ${nexusParams}, file: ${fileAbsolutePath}")
        def uploadUri = nexus.storeComplextArtifact(
            repository,
            new File("${fileAbsolutePath}").getBytes(),
            'application/octet-stream',
            repositoryType,
            nexusParams)
        script.echo "Uploaded '${distFile}' to '${uploadUri}'"
        return uploadUri
    }

}
