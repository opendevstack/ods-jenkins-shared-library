package org.ods.orchestration.usecase

class ComponentMismatchException  extends IllegalArgumentException {

    ComponentMismatchException(String message, Throwable t) {
        super(message, t)
    }

    ComponentMismatchException(String message) {
        super(message)
    }
}
