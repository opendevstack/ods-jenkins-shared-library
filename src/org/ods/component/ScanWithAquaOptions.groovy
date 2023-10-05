package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class ScanWithAquaOptions extends Options {

    /**
     * Name of `BuildConfig`/`ImageStream` of the image that we want to scan (defaults to `context.componentId`).
     * BuildOpenShiftImageStage puts the imageRef into a map with the `resourceName` as key.
     * In order to be able to receive the imageRef for scanning, the `resourceName` needs
     * to be the same as in BuildOpenShiftImageStage. */
    String resourceName

    /**
     * Timeout of scan (defaults to 5 minutes - 300 seconds). */
    Integer scanTimeoutSeconds

}
