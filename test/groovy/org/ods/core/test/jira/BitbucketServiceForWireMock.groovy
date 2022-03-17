package org.ods.core.test.jira

import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic
import kong.unirest.Unirest
import org.ods.services.BitbucketService
import org.ods.util.AuthUtil
import org.ods.util.ILogger

class BitbucketServiceForWireMock extends BitbucketService{

    final String originalBitbucketUrl
    String username
    String password
    String project

    @Deprecated
    BitbucketServiceForWireMock(Object script, String bitbucketUrl, String project, String passwordCredentialsId, ILogger logger) {
        super(script, bitbucketUrl, project, passwordCredentialsId, logger)
        throw new RuntimeException("Invalid way to instantiate. Sorry.")
    }

    BitbucketServiceForWireMock(String bitbucketUrl, String username, String password, String project, ILogger logger) {
        super(null, bitbucketUrl, project, "", logger)

        this.username = username
        this.password = password
        this.project = project
        this.originalBitbucketUrl = System.properties["bitbucket.url"]
    }

    @Override
    String getToken() {
        String request = "${originalBitbucketUrl}/rest/access-tokens/1.0/users/${username.replace('@', '_')}"
        // String request = "${bitbucketUrl}/rest/api/1.0/projects/${project}/repos/${repo}/commits"
        Map result = queryRepoPost(request)
        result.token
    }

    @NonCPS
    private Map<String, String> buildHeadersUsernamePassword() {
        Map<String, String> headers = [:]
        headers.put("accept", "application/json")
        headers.put("Content-Type", "application/json")
        String auth = AuthUtil.headerValue(AuthUtil.SCHEME_BASIC, username, password)
        headers.put("Authorization", auth)
        return headers
    }

    @NonCPS
    private Map queryRepoPost(String request) {
        String openShiftCdProject = "${project}-cd"
        Map<String, String> headers = buildHeadersUsernamePassword()
        def payload = """{"name": "ods-jenkins-shared-library-${openShiftCdProject}", "permissions": ["PROJECT_WRITE", "REPO_WRITE"]}"""

        def httpRequest = Unirest.post(request).headers(headers).body(payload)
        def response = httpRequest.asString()

        response.ifFailure {
            def message = 'Error: unable to get data from Bitbucket responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."
            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

}
