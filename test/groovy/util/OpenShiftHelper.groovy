package util

class OpenShiftHelper {
    static def validateLabels(script, project, resources, labels, selector = null) {
        assert script =~ /^\s*oc\s+label\s/
        assert script =~ /\s--overwrite\b/
        if (project) {
            assert script =~ /\s-n\s+\Q${project}\E(?:\s|$)/
        } else {
            assert !(script =~ /\s-n\b/)
        }
        assert script =~ /\s\Q${resources}\E(?:\s|$)/
        if (selector) {
            assert script =~ /\s-l\s+\Q${selector}\E(?:\s|$)/
        } else {
            assert !(script =~ /\s-l\b/)
        }
        labels.each { key, value ->
            if (value != null) {
                assert script =~ /\s\Q${resources}\E\s(?:.*?\s)?\Q${key}='${value}'\E(?:\s|$)/
            } else {
                assert script =~ /\s\Q${resources}\E\s(?:.*?\s)?\Q${key}-\E(?:\s|$)/
            }
        }
        return true
    }
}
