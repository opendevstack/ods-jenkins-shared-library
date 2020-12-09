import org.ods.component.IContext

import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:], Closure block) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    OpenShiftService openShift = ServiceRegistry.instance.get(OpenShiftService)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }

    if (context.triggeredByOrchestrationPipeline) {
        logger.info(
            '''Orchestration pipeline runs always execute the 'orElse' block ''' +
            'as image building is handled in the orchestration pipeline.'
        )
        return block()
    }

    if (!config.resourceName) {
        config.resourceName = context.componentId
    }
    if (!config.imageTag) {
        config.imageTag = context.shortGitCommit
    }

    logger.info("Looking for existing '${config.resourceName}' image in '${context.cdProject}' ...")
    def imageExists = openShift.imageExists(context.cdProject, config.resourceName, config.imageTag)

    if (imageExists) {
        logger.info(
            "Image '${config.resourceName}:${config.imageTag}' exists already in '${context.cdProject}'. " +
            "The 'orElse' block will not be executed."
        )
        def imageReference = openShift.getImageReference(context.cdProject, config.resourceName, config.imageTag)
        def info = [image: imageReference]
        context.addBuildToArtifactURIs(config.resourceName, info)
        return
    }

    logger.info(
        "Image '${config.resourceName}:${config.imageTag}' does not exist yet in '${context.cdProject}', " +
        "executing the 'orElse' block now ..."
    )
    block()
}

return this
