package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class CopyImageOptions extends Options {

    /**
     * Source image to import
     *
     * This needs to be in the following format: [REGISTRY/]REPO/IMAGE[:TAG]
     */
    String sourceImageUrlIncludingRegistry

    String registry
    String repo
    String image
    String imageTag

    /**
     * tagIntoTargetEnv decides whether or not to create an ImageStream
     */
    Boolean tagIntoTargetEnv

    /**
     * sourceCredential is the token to use, if any, to access the source registry
     */
    String sourceCredential


}
