package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class ImportOpenShiftImageOptions extends Options {

    /**
     * Name of `BuildConfig`/`ImageStream` to use (defaults to `context.componentId`). */
    String resourceName

    /**
     * OpenShift project from which to import the image identified by `resourceName`. */
    String sourceProject

    /**
     * Image tag to look for in the `sourceProject` (defaults to `context.shortGitCommit`). */
    String sourceTag

    /**
     * Image tag to apply to the imported image in the target project (defaults to `sourceTag`). */
    String targetTag

    /**
     * Name of image-puller secret (optional, used when pulling images from an external source cluster). */
    String imagePullerSecret

}
