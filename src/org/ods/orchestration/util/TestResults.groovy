package org.ods.orchestration.util

class TestResults {

    int skipped;

    int succeeded;

    int failed;

    int error;

    TestResults() {
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

    @Override
    public String toString() {
        return "TestResults{" +
            "skipped=" + skipped +
            ", succeeded=" + succeeded +
            ", failed=" + failed +
            ", error=" + error +
            '}';
    }
}
