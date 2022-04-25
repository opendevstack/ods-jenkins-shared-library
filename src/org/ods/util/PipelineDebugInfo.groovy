package org.ods.util

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

    private getStepsEnvDebugInfo(PipelineSteps steps) {
        Map result = [:]
        List keysList = steps.env.keySet().toList()
        for (int i=0; i<keysList.size(); i++) {
            String key = keysList.get(i) as String
            String value = steps.env.get(key) as String
            result.put(key, value)
        }
    }
}
