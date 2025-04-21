package org.ods.orchestration.util

class TestResults {

    private Integer skipped;

    private Integer succeeded;

    private Integer failed;

    private Integer error;

    TestResults(Integer skipped, Integer succeeded, Integer failed, Integer error) {
        this.skipped = skipped
        this.succeeded = succeeded
        this.failed = failed
        this.error = error
    }

    @Override
    public String toString() {
        return "TestResults{" +
            "skipped=" + skipped +
            ", succeeded=" + succeeded +
            ", failed=" + failed +
            '}';
    }
}
