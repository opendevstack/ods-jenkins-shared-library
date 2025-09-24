package org.ods.util

class JenkinsfilePathResolver {

    /**
     * Determine Jenkinsfile / pipeline script path.
     * Parameters mirror the previous Pipeline#getJenkinsfileScriptPath logic.
     */
    static String getJenkinsfileScriptPath(def script, def logger, def context, boolean localCheckoutEnabled) {
        try {
            def scmConfig = script.scm
            if (scmConfig && scmConfig.hasProperty('scriptPath')) {
                logger.debug("Found script path from SCM config: ${scmConfig.scriptPath}")
                return scmConfig.scriptPath
            }
        } catch (Exception e) {
            logger.debug("Could not get script path from SCM configuration: ${e.message}")
        }

        try {
            if (localCheckoutEnabled && context?.cdProject) {
                def pipelinePrefix = "${context.cdProject}/${context.cdProject}-"
                def buildConfigName = script.env.JOB_NAME?.substring(pipelinePrefix.size())
                if (buildConfigName) {
                    def contextDir = script.sh(
                        returnStdout: true,
                        label: 'getting pipeline script context directory from build config',
                        script: "oc get bc/${buildConfigName} -n ${context.cdProject} " +
                            "-o jsonpath='{.spec.source.contextDir}' 2>/dev/null || echo ''"
                    ).trim()

                    if (contextDir) {
                        logger.debug("Found context directory from build config: ${contextDir}")
                        return "${contextDir}/Jenkinsfile"
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Could not get script path from OpenShift build config: ${e.message}")
        }

        logger.debug("Could not determine script path, returning null")
        return null
    }
}
