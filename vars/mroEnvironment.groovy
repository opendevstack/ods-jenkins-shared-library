import org.ods.util.MROPipelineUtil

def call(boolean debug = false) {
    return new MROPipelineUtil(this).getEnvironment(debug)
}

return this
