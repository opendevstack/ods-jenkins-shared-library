import org.ods.util.MROPipelineUtil
import org.ods.util.PipelineSteps

def call(boolean debug = false) {
    def steps = new PipelineSteps(this)
    return new MROPipelineUtil(steps).getBuildEnvironment(debug)
}

return this
