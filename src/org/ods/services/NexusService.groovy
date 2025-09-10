package org.ods.services

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')

import com.cloudbees.groovy.cps.NonCPS
import kong.unirest.ContentType
import kong.unirest.Unirest
import org.apache.http.client.utils.URIBuilder
import org.ods.util.IPipelineSteps

import java.nio.file.Path
import java.nio.file.Paths

class NexusService {
    static final String NEXUS_REPO_EXISTS_KEY = 'nexusRepoExists'

    final private URI baseURL
    final private IPipelineSteps steps
    final private String credentialsId

    NexusService(String baseURL, IPipelineSteps steps, String credentialsId) {
        if (!baseURL?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'baseURL' is undefined.")
        }

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException(
                "Error: unable to connect to Nexus. '${baseURL}' is not a valid URI."
            ).initCause(e)
        }

        if (!steps) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'steps' is null.")
        }

        if (!credentialsId?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'credentialsId' is undefined")
        }

        this.steps = steps
        this.credentialsId = credentialsId
    }

    static NexusService newFromEnv(def env, IPipelineSteps steps, String credentialsId) {
        def c = readConfigFromEnv(env)
        new NexusService(c.nexusUrl as String, steps, credentialsId)
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
    URI storeComplextArtifact(String repository, byte[] artifact, String contentType,
                              String repositoryType, Map nexusParams = [ : ]) {
        def restCall
        steps.withCredentials([
            steps.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'USERNAME',
                passwordVariable: 'PASSWORD'
            )
        ]) {
            restCall = Unirest.post("${this.baseURL}/service/rest/v1/components?repository={repository}")
                .routeParam('repository', repository).basicAuth(steps.env.USERNAME, steps.env.PASSWORD)
        }

        return processStoreArtifactRes(restCall, repository, artifact, contentType, repositoryType, nexusParams)
    }

    Path buildXunitZipFile(def steps, String testDir, String zipFileName) {
        if (!testDir || !steps.fileExists(testDir)) {
            throw new IllegalArgumentException("Error: The test directory '${testDir}' does not exist.")
        }

        // Create a temporary directory inside the workspace
        Path tmpDir = Paths.get(steps.env.WORKSPACE, "tmp")
        Files.createDirectories(tmpDir)

        // Define the ZIP file path in the tmp directory
        Path zipFilePath = tmpDir.resolve(zipFileName)

        // Run shell command to zip contents of testDir into tmp folder
        steps.sh "cd ${testDir} && zip -r '${zipFilePath.toString()}' ."

        if (!Files.exists(zipFilePath) || Files.size(zipFilePath) == 0) {
            throw new RuntimeException("Error: The ZIP file was not created correctly at '${zipFilePath}'.")
        }

        return zipFilePath
    }

    @SuppressWarnings(['LineLength', 'JavaIoPackageAccess', 'ParameterCount'])
    @NonCPS
    private URI processStoreArtifactRes(def restCall, String repository, byte[] artifact,
                                        String contentType, String repositoryType, Map nexusParams = [ : ]) {

        nexusParams.each { key, value ->
            restCall = restCall.field(key, value)
        }

        if (repositoryType == 'pypi') {
            restCall = restCall.field("pypi.asset",
                new ByteArrayInputStream(artifact),
                ContentType.create(contentType),
                nexusParams['pypi.asset'].substring( nexusParams['pypi.asset'].lastIndexOf("/") + 1 ))
        }
        else {
            restCall = restCall.field(
                repositoryType == 'raw' || repositoryType == 'maven2' ? "${repositoryType}.asset1" : "${repositoryType}.asset",
                new ByteArrayInputStream(artifact), contentType)
        }

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
    Map<URI, File> retrieveArtifact(String nexusRepository, String nexusDirectory, String name, String extractionPath) {
        // https://nexus3-ods....../repository/leva-documentation/odsst-WIP/DTP-odsst-WIP-108.zip
        String urlToDownload = "${this.baseURL}/repository/${nexusRepository}/${nexusDirectory}/${name}"
        def restCall
        steps.withCredentials([
            steps.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'USERNAME',
                passwordVariable: 'PASSWORD'
            )
        ]) {
            restCall = Unirest.get("${urlToDownload}").basicAuth(steps.env.USERNAME, steps.env.PASSWORD)
        }
        return (processRetrieveArtifactRes(restCall, urlToDownload, nexusRepository, nexusDirectory, name, extractionPath))
    }

    @SuppressWarnings(['LineLength', 'JavaIoPackageAccess', 'ParameterCount'])
    @NonCPS
    private Map<URI, File> processRetrieveArtifactRes(def restCall, String urlToDownload, String nexusRepository,
                                                      String nexusDirectory, String name, String extractionPath) {
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
        def response
        steps.withCredentials([
            steps.usernamePassword(
                credentialsId: credentialsId,
                usernameVariable: 'USERNAME',
                passwordVariable: 'PASSWORD'
            )
        ]) {
            response = Unirest.get("${urlToDownload}")
                .basicAuth(steps.env.USERNAME, steps.env.PASSWORD)
                .asString()
        }
        response.ifFailure {
            throw new RuntimeException("Could not retrieve data from '${urlToDownload}'")
        }
        return !response.getBody().contains('\"items\" : [ ]')

    }
}
