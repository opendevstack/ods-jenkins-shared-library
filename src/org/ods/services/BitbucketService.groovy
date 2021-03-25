package org.ods.services

import groovy.json.JsonSlurperClassic
import kong.unirest.Unirest
import org.ods.util.ILogger
import com.cloudbees.groovy.cps.NonCPS
import org.ods.util.AuthUtil

@SuppressWarnings('PublicMethodsBeforeNonPublicMethods')
class BitbucketService {

    // file name used to write token secret yaml
    static final String BB_TOKEN_SECRET = '.bb-token-secret.yml'

    private final def script

    // Bae URL of Bitbucket server, such as "https://bitbucket.example.com".
    private final String bitbucketUrl

    // Name of Bitbucket project, such as "foo".
    // This name is also the prefix for OpenShift projects ("foo-cd", "foo-dev", ...).
    private final String project

    // Name of the CD project in OpenShift, based on the "project" name.
    private final String openShiftCdProject

    // Name of the credentials which store the username/password of a user with
    // access to the BitBucket server identified by "bitbucketUrl".
    private final String passwordCredentialsId

    // Name of the secret in "openShiftCdProject", which contains the username/token
    // of a user with access to the BitBucket server identified by "bitbucketUrl".
    // This secret does not need to exist ahead of time, it will be created
    // automatically through the API using "passwordCredentialsId".
    private final String tokenSecretName

    // Name of the credentials which store the username/token of a user with
    // access to the BitBucket server identified by "bitbucketUrl".
    // These credentials do not need to exist ahead of time, it will be created
    // automatically (by syncing the "tokenSecretName").
    private String tokenCredentialsId

    private final ILogger logger

    BitbucketService(def script, String bitbucketUrl, String project,
        String passwordCredentialsId, ILogger logger) {
        this.script = script
        this.bitbucketUrl = bitbucketUrl
        this.project = project
        this.openShiftCdProject = "${project}-cd"
        this.passwordCredentialsId = passwordCredentialsId
        this.tokenSecretName = 'cd-user-bitbucket-token'
        this.logger = logger
    }

    static BitbucketService newFromEnv(
        def script,
        def env,
        String project,
        String passwordCredentialsId,
        ILogger logger) {
        def c = readConfigFromEnv(env)
        new BitbucketService(script, c.bitbucketUrl, project, passwordCredentialsId, logger)
    }

    static Map readConfigFromEnv(def env) {
        def config = [:]
        if (env.BITBUCKET_URL?.trim()) {
            config.bitbucketUrl = env.BITBUCKET_URL.trim()
        } else if (env.BITBUCKET_HOST?.trim()) {
            config.bitbucketUrl = "https://${env.BITBUCKET_HOST.trim()}"
        } else {
            throw new IllegalArgumentException("Environment variable 'BITBUCKET_URL' is required")
        }
        config
    }

    @NonCPS
    static String userTokenSecretYml(String tokenSecretName, String username, String password) {
        """\
          apiVersion: v1
          data:
            username: ${AuthUtil.base64(username)}
            password: ${AuthUtil.base64(password)}
          kind: Secret
          type: kubernetes.io/basic-auth
          metadata:
            name: ${tokenSecretName}
            labels:
              credential.sync.jenkins.openshift.io: 'true'
        """.stripIndent()
    }

    String getUrl() {
        bitbucketUrl
    }

    String getPasswordCredentialsId() {
        passwordCredentialsId
    }

    // Get pull requests of "repo" in given "state" (can be OPEN, DECLINED or MERGED).
    String getPullRequests(String repo, String state = 'OPEN') {
        String res
        withTokenCredentials { username, token ->
            res = script.sh(
                label: 'Get pullrequests via API',
                script: """curl \\
                  --fail \\
                  -sS \\
                  --header \"Authorization: Bearer ${token}\" \\
                  ${bitbucketUrl}/rest/api/1.0/projects/${project}/repos/${repo}/pull-requests?state=${state}""",
                returnStdout: true
            ).trim()
        }
        res
    }

    Map findPullRequest(String repo, String branch, String state = 'OPEN') {
        def apiResponse = getPullRequests(repo, state)
        def prCandidates = []
        try {
            def js = script.readJSON(text: apiResponse)
            prCandidates = js['values']
            if (prCandidates == null) {
                throw new RuntimeException('Field "values" of JSON response must not be empty!')
            }
        } catch (Exception ex) {
            logger.warn "Could not understand API response. Error was: ${ex}"
            return [:]
        }
        for (def i = 0; i < prCandidates.size(); i++) {
            def prCandidate = prCandidates[i]
            try {
                def prFromBranch = prCandidate['fromRef']['displayId']
                if (prFromBranch == branch) {
                    return [
                        key: prCandidate['id'],
                        base: prCandidate['toRef']['displayId'],
                    ]
                }
            } catch (Exception ex) {
                logger.warn "Unexpected API response. Error was: ${ex}"
                return [:]
            }
        }
        return [:]
    }

    @SuppressWarnings('LineLength')
    void postComment(String repo, int pullRequestId, String comment) {
        withTokenCredentials { username, token ->
            def payload = """{"text":"${comment}"}"""
            script.sh(
                label: "Post comment to PR#${pullRequestId}",
                script: """curl \\
                  --fail \\
                  -sS \\
                  --request POST \\
                  --header \"Authorization: Bearer ${token}\" \\
                  --header \"Content-Type: application/json\" \\
                  --data '${payload}' \\
                  ${bitbucketUrl}/rest/api/1.0/projects/${project}/repos/${repo}/pull-requests/${pullRequestId}/comments"""
            )
        }
    }

    @SuppressWarnings('LineLength')
    void setBuildStatus(String buildUrl, String gitCommit, String state, String buildName) {
        logger.debugClocked("buildstatus-${buildName}-${state}",
            "Setting Bitbucket build status to '${state}' on commit '${gitCommit}' / '${buildUrl}'")
        withTokenCredentials { username, token ->
            def maxAttempts = 3
            def retries = 0
            def payload = "{\"state\":\"${state}\",\"key\":\"${buildName}\",\"name\":\"${buildName}\",\"url\":\"${buildUrl}\"}"
            while (retries++ < maxAttempts) {
                try {
                    script.sh(
                        label: 'Set bitbucket build status via API',
                        script: """curl \\
                            --fail \\
                            -sS \\
                            --request POST \\
                            --header \"Authorization: Bearer ${token}\" \\
                            --header \"Content-Type: application/json\" \\
                            --data '${payload}' \\
                            ${bitbucketUrl}/rest/build-status/1.0/commits/${gitCommit}"""
                    )
                    return
                } catch (err) {
                    logger.warn("Could not set Bitbucket build status to '${state}' due to: ${err}")
                }
            }
        }
        logger.debugClocked("buildstatus-${buildName}-${state}")
    }

    def withTokenCredentials(Closure block) {
        if (!tokenCredentialsId) {
            createUserTokenIfMissing()
        }
        script.withCredentials([
            script.usernamePassword(
                credentialsId: tokenCredentialsId,
                usernameVariable: 'USERNAME',
                passwordVariable: 'TOKEN'
            )
        ]) {
            block(script.env.USERNAME, script.env.TOKEN)
        }
    }

    @SuppressWarnings('SynchronizedMethod')
    private synchronized void createUserTokenIfMissing() {
        def credentialsId = "${openShiftCdProject}-${tokenSecretName}"

        if (basicAuthCredentialsIdExists(credentialsId)) {
            logger.debug "Secret ${tokenSecretName} exists and is synced."
            this.tokenCredentialsId = credentialsId
            return
        }

        def credentialsAvailable = false
        logger.info "Secret ${tokenSecretName} does not exist yet, it will be created now."
        def token = createUserToken()
        if (token['password']) {
            createUserTokenSecret(token['username'], token['password'])
        }

        // Ensure that secret is synced to Jenkins before continuing.
        def waitTime = 10 // seconds
        def retries = 3
        for (def i = 0; i < retries; i++) {
            if (basicAuthCredentialsIdExists(credentialsId)) {
                credentialsAvailable = true
                break
            } else {
                logger.debug "Waiting ${waitTime} for credentials '${credentialsId}' to become available."
                script.sleep(waitTime)
                waitTime = waitTime * 2
            }
        }
        if (credentialsAvailable) {
            this.tokenCredentialsId = credentialsId
        } else {
            throw new RuntimeException(
                "ERROR: Secret ${openShiftCdProject}/${tokenSecretName} has been created, " +
                "but credentials '${credentialsId}' are not available. " +
                'Please ensure that the secret is synced and re-run the pipeline.'
            )
        }
    }

    @SuppressWarnings('LineLength')
    Map<String, String> createUserToken() {
        Map<String, String> tokenMap = [username: '', password: '']
        def res = ''
        def payload = """{"name": "ods-jenkins-shared-library-${openShiftCdProject}", "permissions": ["PROJECT_WRITE", "REPO_WRITE"]}"""
        script.withCredentials(
            [script.usernamePassword(
                credentialsId: passwordCredentialsId,
                usernameVariable: 'USERNAME',
                passwordVariable: 'PASSWORD'
            )]
        ) {
            String username = script.env.USERNAME
            tokenMap['username'] = username
            String password = script.env.PASSWORD
            String url = "${bitbucketUrl}/rest/access-tokens/1.0/users/${username.replace('@', '_')}"
            script.echo "Requesting token via PUT ${url} with payload=${payload}"
            res = script.sh(
                returnStdout: true,
                script: """set +x; curl \\
                  --fail \\
                  -sS \\
                  --request PUT \\
                  --header \"Content-Type: application/json\" \\
                  --header \"${AuthUtil.header(AuthUtil.SCHEME_BASIC, username, password)}\" \\
                  --data '${payload}' \\
                  ${url}
                """
            ).trim()
            try {
                // call readJSON inside of withCredentials block,
                // otherwise token will be displayed in output
                def js = script.readJSON(text: res)
                String token = js['token']
                tokenMap['password'] = token
            } catch (Exception ex) {
                logger.warn "Could not understand API response. Error was: ${ex}"
            }
        }
        return tokenMap
    }

    @NonCPS
    String getToken() {
        withTokenCredentials { username, token -> return token}
    }

    @NonCPS
    Map getCommitsForMainBranch(String token, String repo, int limit, int nextPageStart){
        String request = "${bitbucketUrl}/rest/api/1.0/projects/${project}/repos/${repo}/commits"
        return queryRepo(token, request, limit, nextPageStart)
    }

    @NonCPS
    Map getPRforMergedCommit(String token, String repo, String commit) {
        String request = "${bitbucketUrl}/rest/api/1.0/projects/${project}" +
            "/repos/${repo}/commits/${commit}/pull-requests"
        return queryRepo(token, request, 0, 0)
    }

    private Map queryRepo(String token, String request, int limit, int nextPageStart) {
        Map<String, String> headers = buildHeaders(token)
        def httpRequest = Unirest.get(request).headers(headers)
        if (limit>0) {
            httpRequest.queryString("limit", limit)
        }
        if (nextPageStart>0) {
            httpRequest.queryString("start", nextPageStart)
        }
        def response = httpRequest.asString()

        response.ifFailure {
            def message = 'Error: unable to get data from Bitbucket responded with code: ' +
                    "'${response.getStatus()}' and message: '${response.getBody()}'."
            throw new RuntimeException(message)
        }

        return new JsonSlurperClassic().parseText(response.getBody())
    }

    private Map<String, String> buildHeaders(String token) {
        Map<String, String> headers = [:]
        headers.put("accept", "application/json")
        headers.put("Authorization", "Bearer ".concat(token))
        return headers
    }

    private void createUserTokenSecret(String username, String password) {
        String secretYml = userTokenSecretYml(tokenSecretName, username, password)
        script.writeFile(
            file: BB_TOKEN_SECRET,
            text: secretYml
        )
        try {
            script.sh """
                oc -n ${openShiftCdProject} create -f ${BB_TOKEN_SECRET};
                rm ${BB_TOKEN_SECRET}
            """
        } catch (Exception ex) {
            logger.warn "Could not create secret ${tokenSecretName}. Error was: ${ex}"
        }
    }

    private boolean basicAuthCredentialsIdExists(String credentialsId) {
        try {
            script.withCredentials([
                script.usernamePassword(
                  credentialsId: credentialsId,
                  usernameVariable: 'USERNAME',
                  passwordVariable: 'TOKEN'
                )
            ]) {
                true
            }
        } catch (_) {
            false
        }
    }
}
