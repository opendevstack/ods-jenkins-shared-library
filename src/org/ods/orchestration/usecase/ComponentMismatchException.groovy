package org.ods.orchestration.usecase

class ComponentMismatchException  extends IllegalArgumentException {

    ComponentMismatchException(String message, Throwable t) {
        super(message, t)
        this.components = components
    }

    ComponentMismatchException(String message) {
        super(message)
        this.components = components
    }
}
