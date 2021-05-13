package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class UploadToNexusOptions extends Options {

    /**
     * Type of the Nexus repository. Defaults to `maven2`. */
    String repositoryType

    /**
     * Name of the Nexus repository. Defaults to `candidates`. */
    String repository

    /**
     * Filename. Defaults to `${context.componentId}-${context.tagversion}.tar.gz` */
    String distributionFile

    /**
     * For `repositoryType=maven2`: default is the `groupId` on project level,
     * or in case not set at all `org.opendevstack.${context.projectId}` */
    String groupId

    /**
     * For `repositoryType=maven2`: default is  `context.tagversion` */
    String version

    /**
     * For `repositoryType=maven2`: default is `context.componentId` */
    String artifactId

    /**
     * For `repositoryType=raw`: default is  `context.projectId` */
    String targetDirectory

}
