package org.ods.services

import org.ods.util.ILogger

/**
 * ForgejoScmService
 *
 * Drop-in SCM adapter for Forgejo (Gitea-compatible API).
 * - Base URL: ${FORGEJO_URL} (no trailing slash)
 * - Project path: "owner/repo" passed as 'project' arg (same as ScmBitbucketService's 'project')
 * - Token: Jenkins Secret Text bound to 'passwordCredentialsId' (PAT from Forgejo)
 *
 * Notes:
 * - Bitbucket "Code Insights" is implemented via commit statuses on Forgejo.
 * - "Default reviewers" are read from env (SCM_DEFAULT_REVIEWERS) since Forgejo does not expose
 *   a first-class "default reviewers" API like Bitbucket Server; you can emulate via CODEOWNERS.
 *
 * Endpoints used are documented in Forgejo/Gitea API:
 *   - PRs:         /api/v1/repos/{owner}/{repo}/pulls
 *   - Comments:    /api/v1/repos/{owner}/{repo}/issues/{index}/comments
 *   - Statuses:    /api/v1/repos/{owner}/{repo}/statuses/{sha}
 *   - Tags:        /api/v1/repos/{owner}/{repo}/tags
 *   - Branches:    /api/v1/repos/{owner}/{repo}/branches
 *   - Repo:        /api/v1/repos/{owner}/{repo}   (default_branch)
 *   - PR commits:  /api/v1/repos/{owner}/{repo}/compare/{base}...{head}
 *   - PRs by sha:  /api/v1/repos/{owner}/{repo}/commits/{sha}/pull (sanity-checked)
 */
class ScmForgejoService implements IScmService {

    // --- fields ---
    private final def script
    private final String baseUrl           // e.g., https://forgejo.example.com
    private final String ownerRepo         // e.g., acme/my-repo
    private final String passwordCredentialsId
    private final ILogger logger

    // --- static factory & helpers ---

    static ScmForgejoService newFromEnv(
        def script,
        def env,
        String project,
        String passwordCredentialsId,
        ILogger logger) {
        def url = (env.FORGEJO_URL ?: env.GITEA_URL)?.trim()?.replaceAll('/+$', '')
        if (!url) throw new IllegalStateException("FORGEJO_URL is required")
        return new ScmForgejoService(script, url, project, passwordCredentialsId, logger)
    }

    static Map readConfigFromEnv(def env) {
        [
            url                  : (env.FORGEJO_URL ?: env.GITEA_URL),
            defaultReviewers     : (env.SCM_DEFAULT_REVIEWERS ?: "")
                .split(/\s*,\s*/).findAll { it },
            reviewerConditionsRaw: (env.SCM_REVIEWER_CONDITIONS ?: "[]"),
            provider             : (env.SCM_PROVIDER ?: "forgejo"),
        ]
    }

    /**
     * Convenience: render a k8s Secret YAML with basic auth (username/password).
     * Not Forgejo-specific, retained to preserve pipeline expectations.
     */
    static String userTokenSecretYml(String tokenSecretName, String username, String password) {
        def enc = { s -> s?.getBytes('UTF-8')?.encodeBase64()?.toString() ?: "" }
        return """\
apiVersion: v1
kind: Secret
metadata:
  name: ${tokenSecretName}
type: kubernetes.io/basic-auth
data:
  username: ${enc(username)}
  password: ${enc(password)}
""".stripIndent()
    }

    // --- ctor & getters ---

    ScmForgejoService(def script, String baseUrl, String ownerRepo, String passwordCredentialsId, ILogger logger) {
        this.script = script
        this.baseUrl = baseUrl.trim().replaceAll('/+$', '')
        this.ownerRepo = ownerRepo
        this.passwordCredentialsId = passwordCredentialsId
        this.logger = logger
    }

    String getUrl() { baseUrl }

    String getPasswordCredentialsId() { passwordCredentialsId }

    String getToken() { null } // tokens are scoped via withTokenCredentials

    // --- reviewer helpers (env-driven, see header comment) ---

    String getDefaultReviewerConditions(String repo) {
        // Return raw JSON string from env if present, else '[]'
        def val = script.env.SCM_REVIEWER_CONDITIONS ?: "[]"
        return val
    }

    List<String> getDefaultReviewers(String repo) {
        def val = script.env.SCM_DEFAULT_REVIEWERS ?: ""
        return val.split(/\s*,\s*/).findAll { it }
    }

    // --- Pull requests ---

    /**
     * Create a Pull Request.
     * Forgejo payload:
     *   POST /repos/{owner}/{repo}/pulls
     *   { "title":..., "body":..., "base": "target", "head":"source", "assignees": ["user1","user2"] }
     * (Reviewers are typically requested via UI/CODEOWNERS; we set "assignees" as a practical stand-in.)
     */
    String createPullRequest(String repo, String fromRef, String toRef, String title, String description,
                             List<String> reviewers) {
        def (own, rep) = split(ownerRepo)
        def body = [
            title    : title,
            body     : description ?: "",
            base     : toRef,
            head     : fromRef,
            assignees: reviewers ?: []
        ]
        withTokenCredentials { tok ->
            def resp = http('POST', "/api/v1/repos/${own}/${rep}/pulls", null, body, tok, '200:201')
            return resp.content
        }
    }

    /**
     * PRs that include a given commit:
     * GET /repos/{owner}/{repo}/commits/{sha}/pull
     * We sanity-check the returned PR(s) by comparing PR head SHA/branch to reduce false positives.
     */
    String getPullRequestsForCommit(String repo, String commit) {
        def (own, rep) = split(ownerRepo)
        withTokenCredentials { tok ->
            def r = http('GET', "/api/v1/repos/${own}/${rep}/commits/${commit}/pull", null, null, tok, '200:299')
            return r.content
        }
    }

    /**
     * List PRs by state (OPEN|MERGED|DECLINED/CLOSED ⇒ Forgejo: open|closed|all)
     */
    String getPullRequests(String repo, String state = 'OPEN') {
        def (own, rep) = split(ownerRepo)
        def st = mapState(state)
        def q = [state: st, limit: '50', page: '1'] // server default page size ~50
        withTokenCredentials { tok ->
            def r = http('GET', "/api/v1/repos/${own}/${rep}/pulls", q, null, tok, '200:299')
            return r.content
        }
    }

    /**
     * Find PR by branch name and state.
     * We filter client-side on head.ref because head filter is not consistently exposed across versions.
     */
    Map findPullRequest(String repo, String branch, String state = 'OPEN') {
        def (own, rep) = split(ownerRepo)
        def st = mapState(state)
        withTokenCredentials { tok ->
            def items = []
            int page = 1
            while (true) {
                def r = http('GET', "/api/v1/repos/${own}/${rep}/pulls", [state: st, limit: '50', page: "${page}"], null, tok, '200:299')
                def arr = script.readJSON(text: r.content) as List
                if (!arr) break
                items.addAll(arr)
                if (arr.size() < 50) break
                page++
            }
            def match = items.find { it?.head?.ref == branch }
            return match ?: [:]
        } as Map
    }

    /**
     * Comment on PR (issue) index.
     * POST /repos/{owner}/{repo}/issues/{index}/comments  { "body": "..." }
     */
    void postComment(String repo, int pullRequestId, String comment) {
        def (own, rep) = split(ownerRepo)
        withTokenCredentials { tok ->
            http('POST', "/api/v1/repos/${own}/${rep}/issues/${pullRequestId}/comments",
                null, [body: comment], tok, '200:201')
        }
    }

    /**
     * Get merged PRs targeting an "integration" branch (pagination supported).
     * We list closed PRs and filter merged==true and base.ref==integration branch (if provided).
     * Return shape: [values: List, nextPageStart: int, isLastPage: boolean]
     */
    Map getMergedPullRequestsForIntegrationBranch(String token, Map repo, int limit, int nextPageStart) {
        def (own, rep) = split(ownerRepo)
        def integration = (repo?.integrationBranch ?: repo?.branch ?: getDefaultBranch(ownerRepo))
        def page = Math.max(nextPageStart, 1)
        def perPage = Math.max(Math.min(limit ?: 50, 100), 1)

        def tok = token ?: null
        withMaybeToken(tok) { effective ->
            def r = http('GET', "/api/v1/repos/${own}/${rep}/pulls",
                [state: 'closed', limit: "${perPage}", page: "${page}"], null, effective, '200:299')
            def arr = script.readJSON(text: r.content) as List
            def merged = arr.findAll { (it?.merged == true) && (it?.base?.ref == integration) }
            def isLast = (arr.size() < perPage)
            return [
                values       : merged,
                nextPageStart: isLast ? page : (page + 1),
                isLastPage   : isLast
            ]
        } as Map
    }

    /**
     * Commits for PR: Resolve via compare API between base and head.
     * Flow:
     *  1) GET PR ⇒ read base.ref and head.ref
     *  2) GET compare /compare/{base}...{head} ⇒ commits array
     * Return: [values: List, nextPageStart: int, isLastPage: boolean]
     */
    Map getCommmitsForPullRequest(String token, String repo, int pullRequest, int limit, int nextPageStart) {
        def (own, rep) = split(ownerRepo)
        def page = Math.max(nextPageStart, 1)
        def perPage = Math.max(Math.min(limit ?: 50, 100), 1)

        def tok = token ?: null
        withMaybeToken(tok) { effective ->
            def pr = http('GET', "/api/v1/repos/${own}/${rep}/pulls/${pullRequest}", null, null, effective, '200:299')
            def prj = script.readJSON(text: pr.content)
            def baseRef = prj?.base?.ref
            def headRef = prj?.head?.ref
            if (!baseRef || !headRef) return [values: [], nextPageStart: page, isLastPage: true]

            // compare returns all commits; we paginate client-side
           // def cmp = http('GET', "/api/v1/repos/${own}/${rep}/compare/${urlenc(baseRef)}...${urlenc(headRef)}",
            // TODO
            def cmp = http('GET', "/api/v1/repos/${own}/${rep}/compare/",
                null, null, effective, '200:299')
            def cmpj = script.readJSON(text: cmp.content)
            def commits = (cmpj?.commits ?: []) as List
            def from = (page - 1) * perPage
            def slice = from < commits.size() ? commits.subList(from, Math.min(from + perPage, commits.size())) : []
            def isLast = (from + perPage) >= commits.size()
            return [values: slice, nextPageStart: isLast ? page : (page + 1), isLastPage: isLast]
        } as Map
    }

    // --- Tags ---

    /**
     * Create (and optionally force-recreate) a tag.
     * POST /repos/{owner}/{repo}/tags  { tag_name, target, message }
     * If force==true and tag exists, we delete it first.
     */
    void postTag(String repo, String startPoint, String tag, Boolean force = true, String message = "") {
        def (own, rep) = split(ownerRepo)
        withTokenCredentials { tok ->
            if (force && tagExists(own, rep, tag, tok)) {
                http('DELETE', "/api/v1/repos/${own}/${rep}/tags/${urlenc(tag)}", null, null, tok, '204:204')
            }
            http('POST', "/api/v1/repos/${own}/${rep}/tags", null,
                [tag_name: tag, target: startPoint, message: message ?: ""],
                tok, '200:201')
        }
    }

    // --- Build status (commit status) ---

    /**
     * Bitbucket build state => Forgejo commit status.
     * POST /repos/{owner}/{repo}/statuses/{sha}
     */
    void setBuildStatus(String buildUrl, String gitCommit, String state, String buildName) {
        def (own, rep) = split(ownerRepo)
        def mapped = [
            'SUCCESS'   : 'success',
            'FAILED'    : 'failure',
            'ERROR'     : 'error',
            'INPROGRESS': 'pending',
            'PENDING'   : 'pending',
            'WARNING'   : 'warning'
        ][(state ?: '').toUpperCase()] ?: 'pending'
        def body = [
            state      : mapped,
            target_url : buildUrl ?: "",
            description: buildName ?: "build",
            context    : buildName ?: "build"
        ]
        withTokenCredentials { tok ->
            http('POST', "/api/v1/repos/${own}/${rep}/statuses/${gitCommit}", null, body, tok, '200:201')
        }
    }

    // --- Branches ---

    String getDefaultBranch(String repo) {
        def (own, rep) = split(ownerRepo)
        withTokenCredentials { tok ->
            def r = http('GET', "/api/v1/repos/${own}/${rep}", null, null, tok, '200:299')
            def j = script.readJSON(text: r.content)
            return j?.default_branch ?: 'main'
        }
    }

    Map findRepoBranches(String repo, String filterText) {
        def (own, rep) = split(ownerRepo)
        withTokenCredentials { tok ->
            def r = http('GET', "/api/v1/repos/${own}/${rep}/branches", null, null, tok, '200:299')
            def arr = script.readJSON(text: r.content) as List
            def filtered = (filterText ? arr.findAll { (it?.name ?: '').contains(filterText) } : arr)
            return [branches: filtered]
        } as Map
    }

    // --- Code insight (mapped to commit status) ---

    void createCodeInsightReport(Map data, String repo, String gitCommit) {
        // Expect: data = [name:'<context>', state:'SUCCESS|FAILED|PENDING|INPROGRESS|ERROR|WARNING', targetUrl:'https://...']
        setBuildStatus((String) data.targetUrl, gitCommit, (String) data.state, (String) data.name)
    }

    // --- Credential helpers ---

    /**
     * Run closure with Jenkins Secret Text (Forgejo PAT) exposed as 'token'.
     */
    def withTokenCredentials(Closure block) {
        script.withCredentials([script.string(credentialsId: passwordCredentialsId, variable: 'FORGEJO_TOKEN')]) {
            block(script.env.FORGEJO_TOKEN)
        }
    }

    /**
     * If a token was provided explicitly, use it; otherwise fall back to Jenkins credentials.
     */
    private def withMaybeToken(String token, Closure block) {
        if (token) return block(token)
        return withTokenCredentials { t -> block(t) }
    }

    /**
     * Try to create a user token. Forgejo supports token creation at:
     *   POST /api/v1/users/{username}/tokens
     * but it requires Basic Auth (username+password) or OTP (admin policies). In most CI use-cases,
     * PATs are created manually. We therefore return a helpful message unless FORGEJO_USER/FORGEJO_PASSWORD are present.
     */
    Map<String, String> createUserToken() {
        def u = script.env.FORGEJO_USER
        def p = script.env.FORGEJO_PASSWORD
        if (!u || !p) {
            return [info: "Create a Personal Access Token in Forgejo (Settings → Applications) and store it as a Secret Text in Jenkins."]
        }
        // If you really want to automate:
        def auth = "Basic " + "${u}:${p}".bytes.encodeBase64().toString()
        def body = [name: "jenkins-${new Date().format('yyyyMMdd-HHmmss')}", scopes: ["write:repository", "read:repository"]]
        def r = script.httpRequest(
            httpMode: 'POST',
            url: "${baseUrl}/api/v1/users/${urlenc(u)}/tokens",
            customHeaders: [
                [name: 'Authorization', value: auth],
                [name: 'Content-Type', value: 'application/json']
            ],
            requestBody: script.writeJSON(returnText: true, json: body),
            validResponseCodes: '200:201')
        def j = script.readJSON(text: r.content)
        return [token: j?.sha1 ?: ""]
    }

    // --- internals ---

    private static String mapState(String state) {
        switch ((state ?: 'OPEN').toUpperCase()) {
            case 'OPEN': return 'open'
            case 'MERGED': return 'closed' // merged PRs appear in 'closed' with merged=true
            case 'DECLINED':
            case 'CLOSED':
            default: return 'closed'
        }
    }

    private List<String> split(String ownerRepo) {
        def parts = ownerRepo.split('/', 2)
        if (parts.size() != 2) throw new IllegalArgumentException("owner/repo expected, got: ${ownerRepo}")
        return [urlenc(parts[0]), urlenc(parts[1])]
    }

    private String urlenc(String s) {
        java.net.URLEncoder.encode(s ?: "", 'UTF-8')
    }

    private boolean tagExists(String own, String rep, String tag, String tok) {
        def r = http('GET', "/api/v1/repos/${own}/${rep}/tags", null, null, tok, '200:299')
        def arr = script.readJSON(text: r.content) as List
        return arr.any { it?.name == tag }
    }

    /**
     * Unified HTTP wrapper using Jenkins httpRequest.
     */
    private def http(String method, String path, Map query, Object body, String token, String okCodes) {
        def q = query?.collect { k, v -> "${urlenc(k)}=${urlenc(v as String)}" }?.join('&')
        def full = q ? "${baseUrl}${path}?${q}" : "${baseUrl}${path}"
        def headers = token ? [[name: 'Authorization', value: "token ${token}"]] : []
        if (['POST', 'PUT', 'PATCH'].contains(method) && body != null) {
            headers << [name: 'Content-Type', value: 'application/json']
            return script.httpRequest(
                httpMode: method,
                url: full,
                customHeaders: headers,
                requestBody: script.writeJSON(returnText: true, json: body),
                validResponseCodes: okCodes ?: '200:299')
        } else {
            return script.httpRequest(
                httpMode: method,
                url: full,
                customHeaders: headers,
                validResponseCodes: okCodes ?: '200:299')
        }
    }
}
