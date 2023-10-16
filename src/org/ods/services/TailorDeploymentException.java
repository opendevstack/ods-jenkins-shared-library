package org.ods.services;

public class TailorDeploymentException extends RuntimeException {

    public TailorDeploymentException() {
    }

    public TailorDeploymentException(String message) {
        super(message);
    }

    public TailorDeploymentException(String message, Throwable cause) {
        super(message, cause);
    }

    public TailorDeploymentException(Throwable cause) {
        super(cause);
    }
}
