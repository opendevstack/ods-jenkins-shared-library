package org.ods.orchestration.phases

class DeployOdsComponentException extends RuntimeException {

    DeployOdsComponentException() {
    }

    DeployOdsComponentException(String message) {
        super(message)
    }

    DeployOdsComponentException(String message, Throwable cause) {
        super(message, cause)
    }

    DeployOdsComponentException(Throwable cause) {
        super(cause)
    }
}
