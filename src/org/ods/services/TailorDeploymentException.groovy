package org.ods.services

class TailorDeploymentException extends RuntimeException {

    TailorDeploymentException() {
    }

    TailorDeploymentException(String message) {
        super(message)
    }

    TailorDeploymentException(String message, Throwable cause) {
        super(message, cause)
    }

    TailorDeploymentException(Throwable cause) {
        super(cause)
    }

    TailorDeploymentException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace)
    }
}
