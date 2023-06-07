package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class ScanWithTrivyOptions extends Options {

    //Add proper exlanation and more options for trivy

    String resourceName

    String format

    String scanners

    String vulType

    String nexusRepository
}
