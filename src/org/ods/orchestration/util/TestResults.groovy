package org.ods.orchestration.util

class TestResults {

    private Integer skipped;

    private Integer succeeded;

    private Integer failed;

    @Override
    public String toString() {
        return "TestResults{" +
            "skipped=" + skipped +
            ", succeeded=" + succeeded +
            ", failed=" + failed +
            '}';
    }
}
