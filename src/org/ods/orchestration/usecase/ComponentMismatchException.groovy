package org.ods.orchestration.usecase

class ComponentMismatchException extends Exception {

    ComponentMismatchException(String message, Throwable t) {
        super(message, t)
    }

    ComponentMismatchException(String message) {
        super(message)
    }

}
