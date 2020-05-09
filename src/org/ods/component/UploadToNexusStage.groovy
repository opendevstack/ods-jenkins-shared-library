package org.ods.component

import org.ods.services.NexusService

class UploadToNexusStage extends Stage {

    public final String STAGE_NAME = 'Upload to Nexus'

    final String repository
    final String repositoryType
    final String distFile
    final NexusService nexus
    
    UploadToNexusStage(def script, IContext context, Map config, NexusService nexus) {
        super(script, context, config)
        this.repository = config.repository ?: 'candidates'
        this.repositoryType = config.repositoryType ?: 'maven2'
        this.distFile = config.distributionFile ?: "${componentId}-${context.tagversion}.tar.gz"
        this.nexus = nexus
    }

    @SuppressWarnings('SpaceAroundMapEntryColon')
    def run() {
        if (!script.fileExists (distFile)) {
            script.error ("Could not upload file ${distFile} - it does NOT exist!")
            return
        }

        Map nexusParams = [ : ]

        if (repositoryType == 'maven2') {
            nexusParams << ['maven2.groupId' : (config.groupId ?: context.groupId.replace('.', '/'))]
            nexusParams << ['maven2.artifactId': (config.artifactId ?: componentId)]
            nexusParams << ['maven2.version': (config.version ?: context.tagversion)]
            nexusParams << ['maven2.asset1.extension': (distFile.substring(distFile.lastIndexOf('.') + 1))]
        } else if (repositoryType == 'raw') {
            nexusParams << ['raw.asset1.filename': distFile]
            nexusParams << ['raw.directory': (config.targetDirectory ?: context.projectId)]
        }

        def data = script.readFile(['file' : distFile, 'encoding' : 'Base64']).getBytes()
        script.echo ("Nexus upload params: ${nexusParams}, " +
            " file: ${distFile} to repo ${nexus.baseURL}/${repository}")
        def uploadUri = nexus.storeComplextArtifact(
            repository,
            Base64.getDecoder().decode(data),
            'application/octet-stream',
            repositoryType,
            nexusParams)
        script.echo "Uploaded '${distFile}' to '${uploadUri}'"
        return uploadUri
    }

}
