package org.ods.services.documents.snow.client

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.util.Project

class SNowCmdbClient {
    private final Project project

    SNowCmdbClient(Project project) {
        this.project = project
    }

    private static final Set<String> SIGNER_ROLES_GXP = [
        "System Owner",
        //"System Owner Delegate(s)",
        "CSV&C/QA Contact",
        //"CSV&C/QA Delegate(s)",
    ].toSet().asImmutable()

    private static final Set<String> SIGNER_ROLES_NON_GXP = [
        "System Owner",
        //"System Owner Delegate(s)",
    ].toSet().asImmutable()

    private final ServiceNowClient client

    SNowCmdbClient() {
        def config = new ServiceNowConfig()
        client = new ServiceNowClient(config)
    }

    @NonCPS
    List<String> getDocumentSigners() {
        def signerRoles = project.gxp ? SIGNER_ROLES_GXP : SIGNER_ROLES_NON_GXP
        def token = client.getAccessToken()
        def businessApplicationRoles = client.getBusinessApplicationRoles(token)
        def signerEmails = client.getUserEmails(token, businessApplicationRoles, signerRoles)
        return signerEmails.values().toUnique() as List
    }
}
