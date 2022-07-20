package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class CopyImageOptions extends Options {

    /**
     * Name of `BuildConfig`/`ImageStream` to use (defaults to `context.componentId`). */
    String resourceName

    // /**
    //  * Image tag to apply (defaults to `context.shortGitCommit`). */
    // String imageTag

    // /**
    //  * Pass build arguments to the image build process. */
    // Map<String, String> buildArgs

    // /**
    //  * Pass labels which should be added on the image.
    //  * Each label will be prefixed with `ext.`. */
    // Map<String, String> imageLabels

    // /**
    //  * Extra image labels added into `imageLabels` */
    // Map<String, String> extensionImageLabels

    // /**
    //  * Timeout of build (defaults to 15 minutes). */
    // Integer buildTimeoutMinutes

    // /**
    //  * Adjust retries to wait for the build pod status (defaults to 5). */
    // Integer buildTimeoutRetries

    // /**
    //  * Docker context directory (defaults to `docker`). */
    // String dockerDir

    /**
     * Source image to import
     *
     * This needs to be in the following format: [REGISTRY/]REPO/IMAGE[:TAG]
     */
    String sourceImageUrlIncludingRegistry

    /**
     * tagIntoTargetEnv decides whether or not to create an ImageStream
     */
    Boolean tagIntoTargetEnv

    /**
     * sourceCredential is the token to use, if any, to access the source registry
     */
    String sourceCredential


}
