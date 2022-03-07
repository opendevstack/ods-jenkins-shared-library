package org.ods.core.test.service

import groovy.util.logging.Slf4j
import kong.unirest.HttpResponse
import kong.unirest.Unirest
import net.lingala.zip4j.ZipFile
import org.springframework.stereotype.Service
import org.springframework.util.FileSystemUtils

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Slf4j
class BitbucketReleaseManagerService {

    void downloadRepo(String project, String repo, String branch, String tmpFolder) {
        String zipArchive = "${tmpFolder}/releaseManagerRepo.zip"
        doRequestFile(project, repo, branch, zipArchive)
        new ZipFile(Paths.get(zipArchive).toFile()).extractAll(tmpFolder)
    }

    private void doRequestFile(String project, String repo, String branch, String zipArchive) {
        String urlBase = System.properties["bitbucket.url"]?: "https://bitbucket-dev.biscrum.com"
        String user = System.properties["bitbucket.username"]
        String password = System.properties["bitbucket.password"]
        String urlFile = "${urlBase}/rest/api/latest/projects/${project}/repos/${repo}/archive?at=${branch}&format=zip"

        log.info("Downloading ${urlBase}")
        HttpResponse<File> response = Unirest.get(urlFile).basicAuth(user, password).asFile(zipArchive)
        response.ifFailure {
            def message = 'Error: unable to get artifact. ' +
                "Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'." +
                " The url called was: ${urlFile}"

            if (response.getStatus() == 404) {
                message = "Error: unable to get artifact. Nexus could not be found at: '${urlFile}'."
            }
            if (response.getStatus() != 200) {
                throw new RuntimeException(message)
            }
        }
    }

}
