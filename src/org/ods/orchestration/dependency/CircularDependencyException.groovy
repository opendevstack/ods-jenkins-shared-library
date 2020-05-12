package org.ods.orchestration.dependency

import groovy.transform.InheritConstructors

@InheritConstructors
class CircularDependencyException extends RuntimeException {

}
