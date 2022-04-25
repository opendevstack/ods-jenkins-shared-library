package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import hudson.EnvVars
import org.ods.orchestration.util.Project

class PipelineDebugInfo {

    public final String PROJECT_DEBUG_INFO_FILENAME = "project_debug_info.yml"
    public final String ENVIRONMENT_DEBUG_INFO_FILENAME = "environment_debug_info.yml"

    void save(Project project, PipelineSteps steps) {

        steps.writeFile(PROJECT_DEBUG_INFO_FILENAME, "${project}")
        steps.archiveArtifacts(PROJECT_DEBUG_INFO_FILENAME)

        Map environmentDebugInfo = getStepsEnvDebugInfo(steps)
        steps.writeFile(ENVIRONMENT_DEBUG_INFO_FILENAME, "${environmentDebugInfo}")
        steps.archiveArtifacts(ENVIRONMENT_DEBUG_INFO_FILENAME)
    }

    private Map getStepsEnvDebugInfo(PipelineSteps steps) {
        EnvVars environmentVariables = steps.getEnv().getEnvironment()
        Set<Map.Entry> entriesSet = environmentVariables.entrySet()
        return getVarsMapFromEntriesSet(entriesSet)
    }

    @NonCPS
    private Map getVarsMapFromEntriesSet(Set entriesSet) {
        Map result = [:]
        List entriesList = entriesSet.toList()
        for (int i=0; i<entriesList.size(); i++) {
            Map.Entry entry = entriesList.get(i)
            String key = entry.getKey() as String
            String value = entry.getValue() as String
            result.put(key, value)
        }
        return result
    }
}
