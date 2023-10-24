package org.ods.services

class TailorDeploymentWarning extends RuntimeException {

    TailorDeploymentWarning() {
    }

    TailorDeploymentWarning(String message) {
        super(message)
    }

    TailorDeploymentWarning(String message, Throwable cause) {
        super(message, cause)
    }

    TailorDeploymentWarning(Throwable cause) {
        super(cause)
    }

    TailorDeploymentWarning(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace)
    }
}
