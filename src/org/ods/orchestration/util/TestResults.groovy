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

    TestResults() {
    }

    TestResults(int skipped, int succeeded, int failed, int error, int missing) {
        this.skipped = skipped
        this.succeeded = succeeded
        this.failed = failed
        this.error = error
        this.missing = missing
    }

    TestResults deepCopy() {
        return new TestResults(skipped, succeeded, failed, error, missing);
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

    boolean equals(o) {
        if (this.is(o)) {
            return true
        }
        if (o == null || getClass() != o.class) {
            return false
        }

        TestResults that = (TestResults) o

        if (error != that.error) {
            return false
        }
        if (failed != that.failed) {
            return false
        }
        if (missing != that.missing) {
            return false
        }
        if (skipped != that.skipped) {
            return false
        }

        return (succeeded != that.succeeded) ? false : true
    }

    int hashCode() {
        int result
        result = skipped
        result = 31 * result + succeeded
        result = 31 * result + failed
        result = 31 * result + error
        result = 31 * result + missing
        return result
    }
}
