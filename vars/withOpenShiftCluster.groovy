import org.ods.component.IContext
import org.ods.services.OpenShiftService
import org.ods.util.PipelineSteps

// You may use withOpenShiftCluster to execute code against another OpenShift cluster.
// It is required to pass the API url (can be retrieved via "oc whoami --show-server")
// of the external cluster via config.apiUrl, and provide the credentials of a
// serviceaccount with appropriate access there. The credentials need to be stored in an
// OpenShift Secret resource which is synced as a Jenkins credential.
//
// To create the serviceaccount in the external cluster, run e.g.:
//   oc create sa <SA-NAME>
//   oc policy add-role-to-user admin system:serviceaccount:<PROJECT-NAME>:<SA-NAME> -n <PROJECT-NAME>
//
// Then, create a secret in the cluster where the Jenkins instance runs, e.g.:
//   oc -n <PROJECT-NAME> create secret generic <SECRET-NAME> \
//     --from-literal=password=$<SA-TOKEN> \
//     --from-literal=username=<SA-NAME> \
//     --type="kubernetes.io/basic-auth"
//   oc -n <PROJECT-NAME> label secret <SECRET-NAME> credential.sync.jenkins.openshift.io=true
//
// The synced Jenkins credential is then named "<PROJECT_KEY>-cd-<SECRET-NAME>", and needs to
// be passed as config.credentialsId.
//
// Example usage:
//
//   withOpenShiftCluster(context, [apiUrl: "https://api.example.com", credentialsId: "foo-cd-sa-example"]) {
//     // Your code here, e.g.
//     // sh "oc whoami --show-server" // prints https://api.example.com
//   }
//
def call(IContext context, Map config = [:], Closure block) {
    // this is to explicitely allow it thru overriding in the config
    if (context.triggeredByOrchestrationPipeline && !config.allow) {
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
