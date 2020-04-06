import org.ods.component.ScanWithSnykStage
import org.ods.component.IContext

import org.ods.services.SnykService
import org.ods.services.ServiceRegistry

def call(IContext context, Map config = [:]) {
    def snykService = ServiceRegistry.instance.get(SnykService)
    if (!snykService) {
        snykService = new SnykService(this, 'snyk-report.txt')
    }
    def stage = new ScanWithSnykStage(this, context, config, snykService)
    stage.execute()
}
return this
