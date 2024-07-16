package org.ods.orchestration.usecase

class ServiceNotReadyException extends Exception {
    final int status

    ServiceNotReadyException(int status){
        this.status = status
    }

    ServiceNotReadyException(int status, String message) {
        super(message)
        this.status = status
    }

    ServiceNotReadyException(int status, Throwable cause) {
        super(cause)
        this.status = status
    }

    ServiceNotReadyException(int status, String message, Throwable cause) {
        super(message, cause)
        this.status = status
    }

    @Override
    String toString() {
        return super.toString() + "\nHTTP status: ${status}"
    }
}
