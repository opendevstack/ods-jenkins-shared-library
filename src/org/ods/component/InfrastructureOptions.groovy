package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class InfrastructureOptions extends Options {

    String cloudProvider
    String resourceName
    String envPath

}
