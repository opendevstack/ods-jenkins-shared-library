package org.ods.dependency

import groovy.transform.InheritConstructors

@InheritConstructors
class CircularDependencyException extends RuntimeException {
}
