package org.ods.services

import org.ods.util.ILogger

class BitbucketService {

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
                  --silent \\
                  --header \"Authorization: Bearer ${token}\" \\
                  ${bitbucketUrl}/rest/api/1.0/projects/${project}/repos/${repo}/pull-requests?state=${state}""",
                returnStdout: true
            ).trim()
        }
        res
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
                        label: 'Set build status via API',
                        script: """curl \\
                            --fail \\
                            --silent \\
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
            block(script.USERNAME, script.TOKEN)
        }
    }

    private void createUserTokenIfMissing() {
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
    private Map<String, String> createUserToken() {
        def tokenMap = [username: '', password: '']
        def res = ''
        def payload = """{"name": "ods-jenkins-shared-library-${openShiftCdProject}", "permissions": ["PROJECT_WRITE", "REPO_WRITE"]}"""
        script.withCredentials(
            [script.usernamePassword(
                credentialsId: passwordCredentialsId,
                usernameVariable: 'USERNAME',
                passwordVariable: 'PASSWORD'
            )]
        ) {
            tokenMap['username'] = script.USERNAME
            res = script.sh(
                returnStdout: true,
                script: """curl \\
                  --fail \\
                  --silent \\
                  --user ${script.USERNAME.replace('$', '\'$\'')}:${script.PASSWORD.replace('$', '\'$\'')} \\
                  --request PUT \\
                  --header \"Content-Type: application/json\" \\
                  --data '${payload}' \\
                  ${bitbucketUrl}/rest/access-tokens/1.0/users/${script.USERNAME.replace('@', '_')}
                """
            ).trim()
            try {
                // call readJSON inside of withCredentials block,
                // otherwise token will be displayed in output
                def js = script.readJSON(text: res)
                tokenMap['password'] = js['token']
            } catch (Exception ex) {
                logger.warn "Could not understand API response. Error was: ${ex}"
            }
        }
        return tokenMap
    }

    private void createUserTokenSecret(String username, String password) {
        script.sh """
        set +x
        oc -n ${openShiftCdProject} create secret generic ${tokenSecretName} \
            --from-literal=password=${password} \
            --from-literal=username=${username} \
            --type=\"kubernetes.io/basic-auth\"
        oc -n ${openShiftCdProject} label secret ${tokenSecretName} credential.sync.jenkins.openshift.io=true
        """
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
