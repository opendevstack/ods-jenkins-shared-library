package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class InfrastructureOptions extends Options {

    /**
     * Name of the cloud provider used to deploy the cloud resources specified
     * by the component. */
    String cloudProvider
    /**
     * Name of `BuildConfig`/`ImageStream` to use (defaults to `context.componentId`). */
    String resourceName
    /**
     * Name of infrastructure service environment folder path to use (defaults to `./environments`). */
    String envPath

}
