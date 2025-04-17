package org.ods.orchestration.util

class TestResults {

    private int skipped;

    private int succeeded;

    private int failed;

    @Override
    public String toString() {
        return "TestResults{" +
            "skipped=" + skipped +
            ", succeeded=" + succeeded +
            ", failed=" + failed +
            '}';
    }
}
