import org.ods.util.Project
import org.ods.util.PipelineSteps

def call(boolean debug = false) {
    def steps = new PipelineSteps(this)
    return Project.getBuildEnvironment(steps, debug)
}

return this
