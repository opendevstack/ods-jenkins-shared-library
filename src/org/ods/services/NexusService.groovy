package org.ods.services

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')

import com.cloudbees.groovy.cps.NonCPS
import kong.unirest.Unirest
import kong.unirest.ContentType
import org.apache.http.client.utils.URIBuilder

class NexusService {

    static final String NEXUS_REPO_EXISTS_KEY = 'nexusRepoExists'
    final URI baseURL

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

        response.ifSuccess {
          println "Download of ${urlToDownload} successfull ${response.getStatus()}: ${response.getBody()}"
        }
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
            url: "${urlToDownload}",
            status: "${response.getStatus()}",
            content: response.getBody(),
            fLocation: "${extractionPath}/${name}",
            exists: new File("${extractionPath}/${name}").exists(),
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

}
