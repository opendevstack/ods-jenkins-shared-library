package org.ods.services

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')

import com.cloudbees.groovy.cps.NonCPS
import com.google.common.base.Strings
import kong.unirest.Unirest
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import org.apache.http.client.utils.URIBuilder
import org.ods.orchestration.util.PipelineUtil
import org.ods.orchestration.util.WeakPair

import java.nio.file.Path
import java.nio.file.Paths

class NexusService {

    static final String NEXUS_REPO_EXISTS_KEY = 'nexusRepoExists'
    static final String DEFAULT_NEXUS_REPOSITORY = "leva-documentation"
    static final String JENKINS_LOG = "jenkins-job-log"
    static final String CONTENT_TYPE = "application/octet-binary"

    URI baseURL

    final String username
    final String password

    NexusService(String baseURL, String username, String password) {
        if (!baseURL?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'baseURL' is undefined.")
        }

        if (!username?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'username' is undefined.")
        }

        if (!password?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'password' is undefined.")
        }

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException(
                "Error: unable to connect to Nexus. '${baseURL}' is not a valid URI."
            ).initCause(e)
        }

        this.username = username
        this.password = password
    }

    static NexusService newFromEnv(def env) {
        def c = readConfigFromEnv(env)
        new NexusService(c.nexusUrl, c.nexusUsername, c.nexusPassword)
    }

    static Map readConfigFromEnv(def env) {
        def config = [:]
        if (env.NEXUS_URL?.trim()) {
            config.nexusUrl = env.NEXUS_URL.trim()
        } else if (env.NEXUS_HOST?.trim()) {
            // Historically, the NEXUS_HOST variable contains the scheme.
            config.nexusUrl = env.NEXUS_HOST.trim()
        } else {
            throw new IllegalArgumentException("Environment variable 'NEXUS_URL' is required")
        }
        if (env.NEXUS_USERNAME?.trim()) {
            config.nexusUsername = env.NEXUS_USERNAME.trim()
        } else {
            throw new IllegalArgumentException('NEXUS_USERNAME is required, but not set')
        }
        if (env.NEXUS_PASSWORD?.trim()) {
            config.nexusPassword = env.NEXUS_PASSWORD.trim()
        } else {
            throw new IllegalArgumentException('NEXUS_PASSWORD is required, but not set')
        }
        config
    }

    @NonCPS
    URI storeArtifact(String repository, String directory, String name, byte[] artifact, String contentType) {
        Map nexusParams = [
            'raw.directory': directory,
            'raw.asset1.filename': name,
        ]

        return storeComplextArtifact(repository, artifact, contentType, 'raw', nexusParams)
    }

    URI storeArtifactFromFile(
        String repository,
        String directory,
        String name,
        File artifact,
        String contentType) {
        return storeArtifact(repository, directory, name, artifact.getBytes(), contentType)
    }

    @SuppressWarnings('LineLength')
    @NonCPS
    URI storeComplextArtifact(String repository, byte[] artifact, String contentType, String repositoryType, Map nexusParams = [ : ]) {
        def restCall = Unirest.post("${this.baseURL}/service/rest/v1/components?repository={repository}")
            .routeParam('repository', repository)
            .basicAuth(this.username, this.password)

        nexusParams.each { key, value ->
            restCall = restCall.field(key, value)
        }

        restCall = restCall.field(
            repositoryType == 'raw' || repositoryType == 'maven2' ? "${repositoryType}.asset1" : "${repositoryType}.asset",
            new ByteArrayInputStream(artifact), contentType)

        def response = restCall.asString()
        response.ifSuccess {
            if (response.getStatus() != 204) {
                throw new RuntimeException(
                    'Error: unable to store artifact. ' +
                        "Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."
                )
            }
        }

        response.ifFailure {
            def message = 'Error: unable to store artifact. ' +
                "Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to store artifact. Nexus could not be found at: '${this.baseURL}' with repo: ${repository}."
            }

            throw new RuntimeException(message)
        }

        if (repositoryType == 'raw') {
            return this.baseURL.resolve("/repository/${repository}/${nexusParams['raw.directory']}/" +
              "${nexusParams['raw.asset1.filename']}")
        }
        return this.baseURL.resolve("/repository/${repository}")
    }

    @SuppressWarnings(['LineLength', 'JavaIoPackageAccess'])
    @NonCPS
    Map<URI, File> retrieveArtifact(String nexusRepository, String nexusDirectory, String name, String extractionPath) {
        // https://nexus3-ods....../repository/leva-documentation/odsst-WIP/DTP-odsst-WIP-108.zip
        String urlToDownload = "${this.baseURL}/repository/${nexusRepository}/${nexusDirectory}/${name}"
        def restCall = Unirest.get("${urlToDownload}")
            .basicAuth(this.username, this.password)

        // hurray - unirest, in case file exists - don't do anything.
        File artifactExists = new File("${extractionPath}/${name}")
        if (artifactExists) {
            artifactExists.delete()
        }
        def response = restCall.asFile("${extractionPath}/${name}")

        response.ifFailure {
            def message = 'Error: unable to get artifact. ' +
                "Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'." +
                " The url called was: ${urlToDownload}"

            if (response.getStatus() == 404) {
                message = "Error: unable to get artifact. Nexus could not be found at: '${urlToDownload}'."
            }
            // very weird, we get a 200 as failure with a good artifact, wtf.
            if (response.getStatus() != 200) {
                throw new RuntimeException(message)
            }
        }

        return [
            uri: this.baseURL.resolve("/repository/${nexusRepository}/${nexusDirectory}/${name}"),
            content: response.getBody(),
        ]
    }

    boolean groupExists(String nexusRepository, String groupName) {
        String urlToDownload =
            "${this.baseURL}/service/rest/v1/search?repository=${nexusRepository}&group=/${groupName}"
        def response = Unirest.get("${urlToDownload}")
            .basicAuth(this.username, this.password)
            .asString()

        response.ifFailure {
            throw new RuntimeException ("Could not retrieve data from '${urlToDownload}'")
        }
        return !response.getBody().contains('\"items\" : [ ]')
    }

    @NonCPS
    String uploadTestsResults(String testType,
                              String projectId,
                              URI testReportsUnstashPath,
                              String workspacePath,
                              String buildId,
                              String repoId = "") {

        String fileName = getFileName(repoId, testType)
        Path zipFilePath = createTemporalZipFile(workspacePath, fileName, testReportsUnstashPath)
        String nexusRepository = NexusService.DEFAULT_NEXUS_REPOSITORY
        String nexusDirectory = "${projectId}/${buildId}".toLowerCase()

        URI report = this.storeArtifact(
            "${nexusRepository}",
            nexusDirectory,
            fileName,
            zipFilePath.getBytes(),
            "application/octet-binary")

        return report.toString()
    }

    @NonCPS
    String uploadJenkinsJobLog(String projectKey, String buildNumber, InputStream jenkinsJobLog, PipelineUtil util) {
        String nexusPath = "${projectKey.toLowerCase()}/${buildNumber}"

        WeakPair<String, InputStream> file = new WeakPair<String, InputStream>(JENKINS_LOG + ".txt", jenkinsJobLog)
        WeakPair<String, InputStream> [] files = [ file ]

        String logFileZipped = "${JENKINS_LOG}.zip"
        byte[] zipArtifact = util.createZipArtifact(logFileZipped, files, true)

        String nexusRepository = NexusService.DEFAULT_NEXUS_REPOSITORY
        URI report = storeArtifact(
            nexusRepository,
            nexusPath,
            logFileZipped,
            zipArtifact,
            CONTENT_TYPE
        )

        return report.toString()
    }

    private Path createTemporalZipFile(String workspacePath, String fileName, URI testReportsUnstashPath) {
        Path tempZipFilePath = Paths.get(workspacePath, fileName)

        def zipFile = new ZipFile(tempZipFilePath.toString())
        ZipParameters zipParameters = new ZipParameters()
        zipParameters.setIncludeRootFolder(false)
        zipFile.addFolder(Paths.get(testReportsUnstashPath).toFile(), zipParameters)

        return zipFile.getFile().toPath()
    }

    private String getFileName(String repoId, String testType) {
        if (testType == "Unit") {
            return ((!Strings.isNullOrEmpty(repoId)) ? "${testType}-${repoId}.zip" : testType).toLowerCase()
        }

        return "${testType}.zip".toLowerCase()
    }

}
