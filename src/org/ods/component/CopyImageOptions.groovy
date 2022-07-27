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

    /**
     * true will tag the image from the -cd namespace into the targetEnvironment that the pipeline is running for
     */
    Boolean tagIntoTargetEnv

    /**
     * sourceCredential is the token to use, if any, to access the source registry
     */
    String sourceCredential

    /**
     * verifyTLS allows the stage to ignore certificate validation errors.
     *
     * The default is to verify certificate paths
     */
    Boolean verifyTLS

    // INTERNAL!!
    /**
     * registry is an INTERNAL value, do not use it */
    String registry
    /**
     * repo is an INTERNAL value, do not use it */
    String repo
    /**
     * image is an INTERNAL value, do not use it */
    String image
    /**
     * imageTag is an INTERNAL value, do not use it */
    String imageTag
}
