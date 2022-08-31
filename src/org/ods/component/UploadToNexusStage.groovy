package org.ods.component

import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.ods.services.NexusService
import org.ods.util.ILogger

@TypeChecked
class UploadToNexusStage extends Stage {

    public final String STAGE_NAME = 'Upload to Nexus'

    private final NexusService nexus
    private final UploadToNexusOptions options

    @TypeChecked(TypeCheckingMode.SKIP)
    UploadToNexusStage(
        def script,
        IContext context,
        Map<String, Object> config,
        NexusService nexus,
        ILogger logger) {
        super(script, context, logger)
        if (!config.repository) {
            config.repository = 'candidates'
        }
        if (!config.repositoryType) {
            config.repositoryType = 'maven2'
        }
        if (!config.distributionFile) {
            config.distributionFile = "${context.componentId}-${context.tagversion}.tar.gz"
        }

        this.options = new UploadToNexusOptions(config)
        this.nexus = nexus
    }

    // This is called from Stage#execute if the branch being built is eligible.
    protected run() {
        if (!steps.fileExists(options.distributionFile)) {
            steps.error("Could not upload file ${options.distributionFile} - it does NOT exist!")
            return
        }

        Map nexusParams = [:]

        if (options.repositoryType == 'maven2') {
            nexusParams << ['maven2.groupId': (options.groupId ?: context.groupId.replace('.', '/'))]
            nexusParams << ['maven2.artifactId': (options.artifactId ?: context.componentId)]
            nexusParams << ['maven2.version': (options.version ?: context.tagversion)]
            def assetExt = options.distributionFile[(options.distributionFile.lastIndexOf('.') + 1)..-1]
            nexusParams << ['maven2.asset1.extension': assetExt]
        } else if (options.repositoryType == 'raw') {
            nexusParams << ['raw.asset1.filename': options.distributionFile]
            nexusParams << ['raw.directory': (options.targetDirectory ?: context.projectId)]
        } else if (options.repositoryType == 'pypi') {
            nexusParams << ['pypi.asset': options.distributionFile]
        }

        def data = steps.readFile(file: options.distributionFile, encoding: 'Base64').toString().getBytes()
        logger.debug("Nexus upload params: ${nexusParams}, " +
            "file: ${options.distributionFile} to repo ${nexus.baseURL}/${options.repository}")
        def uploadUri = nexus.storeComplextArtifact(
            options.repository,
            Base64.getDecoder().decode(data),
            'application/octet-stream',
            options.repositoryType,
            nexusParams
        )
        logger.info("Uploaded '${options.distributionFile}' to '${uploadUri}'")
        uploadUri
    }

}
