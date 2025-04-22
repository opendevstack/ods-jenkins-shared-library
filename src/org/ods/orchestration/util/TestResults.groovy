package org.ods.orchestration.util

class TestResults {

    int skipped;

    int succeeded;

    int failed;

    int error;

    int missing;

    int getSkipped() {
        return skipped
    }

    void setSkipped(int skipped) {
        this.skipped = skipped
    }

    int getSucceeded() {
        return succeeded
    }

    void setSucceeded(int succeeded) {
        this.succeeded = succeeded
    }

    int getFailed() {
        return failed
    }

    void setFailed(int failed) {
        this.failed = failed
    }

    int getError() {
        return error
    }

    void setError(int error) {
        this.error = error
    }

    int getMissing() {
        return missing
    }

    void setMissing(int missing) {
        this.missing = missing
    }

    void addSkipped(int skipped) {
        this.skipped += skipped
    }

    void addSucceeded(int succeeded) {
        this.succeeded += succeeded
    }

    void addFailed(int failed) {
        this.failed += failed
    }

    void addError(int error) {
        this.error += error
    }

    void addMissing(int missing) {
        this.missing += missing
    }

    @Override
    public String toString() {
        return "TestResults{" +
            "skipped=" + skipped +
            ", succeeded=" + succeeded +
            ", failed=" + failed +
            ", error=" + error +
            ", missing=" + missing +
            '}';
    }
}
