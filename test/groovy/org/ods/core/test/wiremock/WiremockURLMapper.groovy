package org.ods.core.test.wiremock

import org.apache.http.client.utils.URIBuilder
import org.ods.core.test.usecase.levadoc.fixture.ProjectFixture
import org.ods.orchestration.usecase.LevaDocWiremock
import org.ods.orchestration.util.Project
import org.springframework.stereotype.Service

import javax.inject.Inject
import org.ods.services.BitbucketService
import org.ods.services.NexusService
import org.ods.orchestration.service.JiraService

@Service
class WiremockURLMapper {

    private final NexusService nexusService
    private final JiraService jiraService
    private final BitbucketService bitbucketService
    private final LevaDocWiremock levaDocWiremock

    @Inject
    WiremockURLMapper(BitbucketService bitbucketService,
                      JiraService jiraService,
                      NexusService nexusService,
                      LevaDocWiremock levaDocWiremock){

        this.bitbucketService = bitbucketService
        this.jiraService = jiraService
        this.nexusService = nexusService
        this.levaDocWiremock = levaDocWiremock
    }

    void updateURLs(Project project) {
        updateServersUrlBase()
        updateDataURL(project)
    }
    private void updateServersUrlBase() {
        nexusService.baseURL = new URIBuilder(levaDocWiremock.nexusServer.server().baseUrl()).build()
        jiraService.baseURL = new URIBuilder(levaDocWiremock.jiraServer.server().baseUrl()).build()
        bitbucketService.bitbucketUrl = levaDocWiremock.bitbucketServer.server().baseUrl()
    }

    private void updateDataURL(Project project) {
        String nexusHostUrl = nexusService.baseURL.toString()
        if (levaDocWiremock.nexusServer.recording) {
            nexusHostUrl = levaDocWiremock.nexusServer.defaultURL
        }
        project.data.build.jenkinLog = replaceHostInUrl(project.data.build.jenkinLog as String, nexusHostUrl)
        project.data.jenkinLog = project.data.build.jenkinLog
        project.data.build.testResultsURLs = updateHostInMapUrls(
                                                        project.data.build.testResultsURLs as Map<String, String>,
                                                        nexusHostUrl)
    }

    private Map updateHostInMapUrls(Map nexusUrls, String newUrl) {
        Map updatedUrls = [:]
        nexusUrls.each {entry ->
            updatedUrls[entry.key] = replaceHostInUrl(entry.value, newUrl)
        }
        return updatedUrls
    }

    private static String replaceHostInUrl(String originalUrl, String newUrl) {
        URI uri = new URI(originalUrl)
        URI newUri = new URI(newUrl)
        URI uriUpdated = new URI(newUri.getScheme(), newUri.getAuthority(), uri.getPath(), uri.getQuery(), uri.getFragment());
        return uriUpdated.toString();
    }
}
