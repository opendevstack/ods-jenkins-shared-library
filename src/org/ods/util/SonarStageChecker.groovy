package org.ods.util

class SonarStageChecker {

    /**
     * Returns true when the pipeline script contains an explicit SonarQube stage.
     */
    static boolean hasSonarStage(def script, def logger, def context, boolean localCheckoutEnabled) {
        try {
            def scriptPath = JenkinsfilePathResolver.getJenkinsfileScriptPath(
                script, logger, context, localCheckoutEnabled
            )
            if (!scriptPath) {
                logger.debug("Could not determine script path, defaulting to 'Jenkinsfile'")
                scriptPath = 'Jenkinsfile'
            }

            def jenkinsfileContent = script.readFile(file: scriptPath)
            def uncommentedContent = CommentRemover.removeCommentedCode(jenkinsfileContent)

            boolean containsSonarStage = uncommentedContent.contains('odsComponentStageScanWithSonar')
            if (containsSonarStage) {
                logger.debug("Found 'odsComponentStageScanWithSonar' in ${scriptPath}")
                return true
            }

            // Also check for deprecated stage name
            containsSonarStage = uncommentedContent.contains('stageScanForSonarqube')
            if (containsSonarStage) {
                logger.debug("Found deprecated 'stageScanForSonarqube' in ${scriptPath}")
                return true
            }

            return false
        } catch (Exception e) {
            logger.warn("Could not read pipeline script to check for SonarQube stage: ${e.message}")
            logger.debug("Full error: ${e}")
            return false
        }
    }

}
