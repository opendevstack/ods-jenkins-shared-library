import org.ods.component.ScanWithTrivyStage
import org.ods.component.IContext

import org.ods.services.TrivyService
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.services.OpenShiftService
import org.ods.services.ServiceRegistry
import org.ods.util.Logger
import org.ods.util.ILogger
import org.ods.util.PipelineSteps

@SuppressWarnings('AbcMetric')
def call(IContext context, Map config = [:]) {
    ILogger logger = ServiceRegistry.instance.get(Logger)
    // this is only for testing, because we need access to the script context :(
    if (!logger) {
        logger = new Logger(this, !!env.DEBUG)
    }

    ServiceRegistry registry = ServiceRegistry.instance
    PipelineSteps steps = registry.get(PipelineSteps)
    if (!steps) {
        steps = new PipelineSteps(this)
        registry.add(PipelineSteps, steps)
    }
    TrivyService trivyService = registry.get(TrivyService)
    if (!trivyService) {
        trivyService = new TrivyService(steps, logger)
        registry.add(TrivyService, trivyService)
    }
    BitbucketService bitbucketService = registry.get(BitbucketService)
    if (!bitbucketService) {
        bitbucketService = new BitbucketService(
            this,
            context.bitbucketUrl,
            context.projectId,
            context.credentialsId,
            logger
        )
        registry.add(BitbucketService, bitbucketService)
    }
    NexusService nexusService = registry.get(NexusService)
    if (!nexusService) {
        nexusService = new NexusService(context.nexusUrl, context.nexusUsername, context.nexusPassword)
        registry.add(NexusService, nexusService)
    }
    OpenShiftService openShiftService = registry.get(OpenShiftService)
    if (!openShiftService) {
        openShiftService = new OpenShiftService(steps, logger)
        registry.add(OpenShiftService, openShiftService)
    }

    String errorMessages = ''
    try {
        new ScanWithTrivyStage(this,
            context,
            config,
            trivyService,
            bitbucketService,
            nexusService,
            openShiftService,
            logger
        ).execute()
    } catch (err) {
        logger.warn("Error with Trivy due to: ${err}")
        errorMessages += "<li>Error with Trivy</li>"
    }
}
return this
