package org.ods.core.test.service

import groovy.json.JsonSlurperClassic
import groovy.util.logging.Slf4j
import kong.unirest.Unirest
import org.apache.http.client.utils.URIBuilder
import org.ods.services.BitbucketService
import org.ods.util.ZipFacade

import javax.inject.Inject
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Slf4j
class BitbucketServiceForWiremock extends BitbucketService {

    String baseURL
    String username
    String password

    @Inject
    ZipFacade zipFacade

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

    void downloadRepoMetadata(String project, String repo, String branch, String defaultBranch, String tmpFolder) {
        log.info("downloadRepo: project:${project}, repo:${repo} and branch:${branch}")
        Path zipArchive = Files.createTempFile("archive-${repo}", ".zip")
        String filePath="metadata.yml"
        try {
            try {
                String url = "${this.baseURL}/rest/api/latest/projects/${project}/repos/${repo}/archive?at=${branch}&format=zip&path=${filePath}"
                Unirest.get(url).asFile(zipArchive.toFile().getAbsolutePath())

            } catch (e) {
                log.warn("Branch [${branch}] doesn't exist, using branch: [${defaultBranch}]")
                String url = "${this.baseURL}/rest/api/latest/projects/${project}/repos/${repo}/archive?at=${defaultBranch}&format=zip&path=${filePath}"
                Unirest.get(url).asFile(zipArchive.toFile().getAbsolutePath())
            }

            zipFacade.extractZipArchive(zipArchive, Paths.get(tmpFolder))
        } catch (e) {
            log.error(e.getMessage(), e)
        } finally {
            Files.delete(zipArchive)
        }
    }
}
