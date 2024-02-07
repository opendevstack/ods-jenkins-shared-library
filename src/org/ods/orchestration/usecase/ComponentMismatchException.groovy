package org.ods.orchestration.usecase

class ComponentMismatchException  extends IllegalArgumentException {
    List<String> components

    ComponentMismatchException(String message, List<String> components, Throwable t) {
        super(message, t)
        this.components = components
    }

    ComponentMismatchException(String message, List<String> components) {
        super(message)
        this.components = components
    }
}
