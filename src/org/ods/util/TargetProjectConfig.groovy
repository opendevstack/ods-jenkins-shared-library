package org.ods.util

import groovy.transform.TypeChecked
import com.cloudbees.groovy.cps.NonCPS

// TargetProjectConfig holds configuration for a target OpenSHift project, which
// could be on the local cluster, or an external cluster.
@TypeChecked
class TargetProjectConfig {

    // namespace is an OpenShift project.
    // Example: foo-dev
    String namespace

    // apiUrl is aan API URL (including scheme and potentially port) of an
    // OpenShift cluster external to the one on which Jenkins runs. The field is
    // required for external clusters but must not be set for local clusters.
    // Example: https://api.example.openshift.com
    String apiUrl

    // apiCredentialsSecret is the name of an OpenShift secret of type
    // "kubernetes.io/basic-auth" which holds credentials of a user with access
    // to the API as specified by apiUrl. The field is required for external
    // clusters but must not be set for local clusters.
    String apiCredentialsSecret

    // registryHost is the (external!) hostname of an OpenShift registry.
    // The field is required for external clusters if the images should be
    // pushed to the target cluster. The field must not be set if images
    // should be pulled from the source cluster, or if the target namespace is
    // on the local cluster.
    String registryHost

    // registrySecret is the name of an OpenShift secret of type
    // "kubernetes.io/dockercfg" or "kubernetes.io/dockerconfigjson". The field
    // is optional. It can be used to control which secret to use to access
    // "registryHost" when images are pushed there. If not set, the secret
    // associated with the "builder" serviceaccount in "namespace" is used.
    String registrySecret

    // skopeoAdditionalFlags is a list of additional flags to add verbatim to
    // the skopeo command execution, such as:
    // ['--src-tls-verify=false', '--dest-tls-verify=false']
    // The field is optional if images should be pushed to the target cluster
    // via skopeo. The field must not be set if images should be pulled from the
    // source cluster, or if the target namespace is on the local cluster.
    List<String> skopeoAdditionalFlags

    // validate checks if the combination of instance variable values is
    // a valid combination. If not, a non-empty string is returned which signals
    // an error with this configuration.
    String validate() {
        if (apiUrl || apiCredentialsSecret || registryHost) {
            if (!apiUrl) {
                return "'apiUrl' is required for external clusters"
            }
            if (!apiCredentialsSecret) {
                return "'apiCredentialsSecret' is required for external clusters"
            }
            if (!registryHost) {
                return "'registryHost' is required for external clusters"
            }
        }
    }

    boolean isLocalCluster() {
        !apiUrl
    }

    boolean isExternalCluster() {
        !!apiUrl
    }

    @NonCPS
    Map<String, Object> toMap() {
        [
            namespace: namespace,
            apiUrl: apiUrl,
            apiCredentialsSecret: apiCredentialsSecret,
            registryHost: registryHost,
            registrySecret: registrySecret,
            skopeoAdditionalFlags: skopeoAdditionalFlags,
        ]
    }

    @NonCPS
    String toString() {
        toMap().toMapString()
    }

}
