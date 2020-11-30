import org.ods.component.ImportOpenShiftImageStage
import org.ods.component.IContext

import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.PipelineSteps
import org.ods.util.Logger
import org.ods.util.ILogger

def call(IContext context, Map config = [:], Closure block) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger (this, !!env.DEBUG)
    }

    if (context.triggeredByOrchestrationPipeline) {
        logger.debug(
            '''Orchestration pipeline builds always run the 'orElse' branch ''' +
            'as image building and importing is handled in the orchestration pipeline.'
        )
        return block()
    }

    if (!context.environment) {
        logger.info('No environment determined, cannot import images.')
        return block()
    }

    if (!config.resourceName) {
        config.resourceName = context.componentId
    }
    if (!config.sourceTag) {
        config.sourceTag = context.shortGitCommit
    }
    if (!config.targetTag) {
        config.targetTag = config.sourceTag
    }

    def namespaceCandidates = [context.targetProject]
    if (config.sourceProject) {
        namespaceCandidates << config.sourceProject
    } else {
        context.imagePromotionSequences.each { sequence ->
            def sequenceParts = sequence.split('->')
            if (sequenceParts.last() == context.environment) {
                namespaceCandidates << "${context.projectId}-${sequenceParts.first()}"
            }
        }
    }
    logger.info("Looking for existing '${config.resourceName}' image in: ${namespaceCandidates.join(', ')}.")
    def namespaceWhereImageExists = namespaceCandidates.find { n ->
        OpenShiftService.imageExists(
            new PipelineSteps(this), n, config.resourceName, config.sourceTag
        )
    }

    if (namespaceWhereImageExists) {
        if (namespaceWhereImageExists == context.targetProject) {
            logger.info("Image '${config.resourceName}' exists already in ${context.targetProject}.")
            return
        }
        logger.info(
            "Image '${config.resourceName}' exists already in ${namespaceWhereImageExists}, " +
            'importing from there ...'
        )
        config.sourceProject = namespaceWhereImageExists
        def stage = new ImportOpenShiftImageStage(
            this, context, config, ServiceRegistry.instance.get(OpenShiftService), logger
        )
        return stage.execute()
    }

    logger.info("Not importing image '${config.resourceName}' ...")
    block()
}
return this
