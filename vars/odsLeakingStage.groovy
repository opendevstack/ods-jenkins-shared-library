import org.ods.services.ServiceRegistry

import org.ods.util.Logger
import org.ods.util.ILogger

import org.ods.orchestration.ThrowingStage

import org.ods.util.UnirestConfig

def call (Map config, Closure stages = null) {
    def debug = config.get('debug', false)
    ServiceRegistry.instance.add(Logger, new Logger(this, debug))
    ILogger logger = ServiceRegistry.instance.get(Logger)
    UnirestConfig.init()
    if (config.get('return', false)) {
        logger.debug '(imported) stage: log via logger'
        echo '(imported) stage: log via echo'
        return
    }
    logger.debug('(root) odsLeakingStage debug')
    try {
        node ('master') {
            writeFile(
                file: "dummy.groovy",
                text: "@Library('ods-jenkins-shared-library@fix/extract_enums') _ \n" +
                    "echo '(imported) groovy: log directly from imported groovy' \n" + 
                    "odsLeakingStage(return : true) \n")
            def data = readFile (file: 'dummy.groovy')
            logger.debug "(root) Created dummy for script for dynamic load: \n ${data}"
            load ('dummy.groovy')
            ThrowingStage.logStatic(logger, '(root) static log')
            new ThrowingStage(this).execute(stages)
        }
    } finally {
        // clear it all ...
        logger.resetStopwatch()
        StringWriter writer = new StringWriter()
        currentBuild.getRawBuild().getLogText().writeLogTo(0, writer)        
        String log = writer.getBuffer().toString()
        currentBuild.result = 'FAILURE'
        ServiceRegistry.removeInstance()
    }
}
return this