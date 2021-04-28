package org.ods.component

import org.ods.services.NexusService
import org.ods.util.ILogger

class UploadToNexusStage extends Stage {

    public final String STAGE_NAME = 'Upload to Nexus'

    final String repository
    final String repositoryType
    final String distFile
    final NexusService nexus

    UploadToNexusStage(def script, IContext context, Map config, NexusService nexus,
        ILogger logger) {
        super(script, context, config, logger)
        this.repository = config.repository ?: 'candidates'
        this.repositoryType = config.repositoryType ?: 'maven2'
        this.distFile = config.distributionFile ?: "${context.componentId}-${context.tagversion}.tar.gz"
        this.nexus = nexus
    }

    protected run() {
        if (!script.fileExists(distFile)) {
            script.error("Could not upload file ${distFile} - it does NOT exist!")
            return
        }

        Map nexusParams = [:]

        if (repositoryType == 'maven2') {
            nexusParams << ['maven2.groupId': (config.groupId ?: context.groupId.replace('.', '/'))]
            nexusParams << ['maven2.artifactId': (config.artifactId ?: context.componentId)]
            nexusParams << ['maven2.version': (config.version ?: context.tagversion)]
            def assetExt = distFile[(distFile.lastIndexOf('.') + 1)..-1]
            nexusParams << ['maven2.asset1.extension': assetExt]
        } else if (repositoryType == 'raw') {
            nexusParams << ['raw.asset1.filename': distFile]
            nexusParams << ['raw.directory': (config.targetDirectory ?: context.projectId)]
        } else if (repositoryType == 'pypi') {
            nexusParams << ['pypi.asset': distFile]
        }

        def data = script.readFile(file: distFile, encoding: 'Base64').getBytes()
        logger.debug("Nexus upload params: ${nexusParams}, " +
            "file: ${distFile} to repo ${nexus.baseURL}/${repository}")
        def uploadUri = nexus.storeComplextArtifact(
            repository,
            Base64.getDecoder().decode(data),
            'application/octet-stream',
            repositoryType,
            nexusParams
        )
        logger.info("Uploaded '${distFile}' to '${uploadUri}'")
        uploadUri
    }

}
