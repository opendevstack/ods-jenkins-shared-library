import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.util.PipelineSteps

def call(IContext context, Map config = [:], Closure block) {
    if (!!env.MULTI_REPO_BUILD) {
        error('withOpenShiftCluster is not supported within an orchestration pipeline context.')
    }

    if (!config.apiUrl) {
        error('''Param 'apiUrl' is required''')
    }
    if (!config.credentialsId) {
        error('''Param 'credentialsId' is required''')
    }
    def steps = new PipelineSteps(this)
    def jenkinsClusterApiUrl = OpenShiftService.getApiUrl(steps)
    withCredentials([
        usernamePassword(
            credentialsId: config.credentialsId,
            usernameVariable: 'EXTERNAL_OPENSHIFT_API_USER',
            passwordVariable: 'EXTERNAL_OPENSHIFT_API_TOKEN'
        )
    ]) {
        def occuredException
        try {
            OpenShiftService.loginToExternalCluster(
                steps, config.apiUrl, EXTERNAL_OPENSHIFT_API_TOKEN
            )
            block(context)
        } catch (ex) {
            occuredException = ex
        } finally {
            sh(
                script: """
                set +x
                oc login ${jenkinsClusterApiUrl} \
                    --token=\$(cat /var/run/secrets/kubernetes.io/serviceaccount/token) \
                    --certificate-authority=/var/run/secrets/kubernetes.io/serviceaccount/ca.crt >& /dev/null
                """,
                label: "Login to OpenShift cluster of Jenkins instance"
            )
        }
        if (occuredException) {
            throw occuredException
        }
    }
}
