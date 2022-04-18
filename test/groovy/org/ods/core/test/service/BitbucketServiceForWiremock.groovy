package org.ods.core.test.service

import groovy.json.JsonSlurperClassic
import kong.unirest.Unirest
import org.ods.services.BitbucketService

class BitbucketServiceForWiremock extends BitbucketService {

    String baseURL
    String username
    String password

    BitbucketServiceForWiremock(String baseURL, String username, String password) {
        super(null, null, null, null, null)

        this.baseURL = baseURL
        this.username = username
        this.password = password
    }

    @Override
    String getBitbucketUrl() {
        return this.baseURL
    }

    @Override
    String getUrl() {
        return this.baseURL
    }

    def getBranchCommits(String project, String repo, String branch, int limit = 1, int start=0) {
        String url = "${this.baseURL}/rest/api/latest/projects/${project}/repos/${repo}/commits/${branch}?limit=${limit}&start=${start}"
        def response = Unirest.get(url)
            .basicAuth(this.username, this.password)
            .header("Accept", "application/json")
            .asString()

        response.ifFailure {
            def message = 'Error: unable to get documentation generation data. Jira responded with code: ' +
                "'${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = 'Error: unable to get documentation generation data. ' +
                    "Jira could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.body)
    }
}
