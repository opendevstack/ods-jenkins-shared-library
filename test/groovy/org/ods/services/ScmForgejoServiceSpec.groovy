package org.ods.services

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Unit tests for ForgejoScmService.
 * We fully mock Jenkins steps (httpRequest, withCredentials, writeJSON, readJSON, string)
 * and assert that the right HTTP calls are made with the expected payloads.
 *
 * NOTE: This test file includes a small FakeScript that mimics Jenkins pipeline behavior.
 */

class ScmForgejoServiceSpec extends Specification {

  // --- Helpers ----------------------------------------------------------------

  static class FakeResponse {
    int status
    String content
  }

  /**
   * Emulates the minimal subset of Jenkins steps used by the adapter:
   *   - env map
   *   - httpRequest(Map)
   *   - withCredentials(List, Closure)
   *   - string(Map)  (used to define credentials binding)
   *   - writeJSON / readJSON
   *
   * Also records 'last' request so we can assert URLs, headers and bodies.
   */
  static class FakeScript {
    def env = [:]
    def last = [:]
    def calls = []

    // emulate credentials binding
    def withCredentials(List bindings, Closure block) {
      // For our tests the adapter sets FORGEJO_TOKEN variable name.
      def var = bindings?.first()?.variable ?: 'FORGEJO_TOKEN'
      env[var] = env[var] ?: 'TKN-123' // inject token if not set
      return block()
    }

    // Jenkins credentials step constructor (not executed)
    def string(Map m) { return [credentialsId: m.credentialsId, variable: m.variable] }

    // Jenkins JSON steps
    def writeJSON(Map args) {
      assert args.returnText == true
      return JsonOutput.toJson(args.json)
    }

    def readJSON(Map args) {
      def slurper = new JsonSlurper()
      return slurper.parseText(args.text as String)
    }

    // Core HTTP mock router
    def httpRequest(Map args) {
      // Record the call for assertions
      last = args
      calls << args

      String url = args.url as String
      String method = args.httpMode as String
      String body = (args.requestBody ?: "") as String

      // Router responses
      if (url.endsWith('/api/v1/repos/acme/my-repo') && method == 'GET') {
        return new FakeResponse(status: 200, content: JsonOutput.toJson([default_branch: 'main']))
      }

      if (url.endsWith('/api/v1/repos/acme/my-repo/branches') && method == 'GET') {
        def branches = [
          [name:'main'], [name:'develop'], [name:'feature/foo'], [name:'bugfix/bar']
        ]
        return new FakeResponse(status: 200, content: JsonOutput.toJson(branches))
      }

      if (url.contains('/api/v1/repos/acme/my-repo/tags')) {
        if (method == 'GET') {
          def list = [
            [name:'v1.0.0', commit:[sha:'abc']],
            [name:'v2.0.0', commit:[sha:'def']]
          ]
          return new FakeResponse(status: 200, content: JsonOutput.toJson(list))
        }
        if (method == 'DELETE') {
          return new FakeResponse(status: 204, content: '')
        }
        if (method == 'POST') {
          def j = new JsonSlurper().parseText(body)
          assert j.tag_name
          return new FakeResponse(status: 201, content: JsonOutput.toJson([name: j.tag_name]))
        }
      }

      if (url.endsWith('/api/v1/repos/acme/my-repo/pulls') && method == 'POST') {
        def j = new JsonSlurper().parseText(body)
        assert j.title && j.base && j.head
        return new FakeResponse(status: 201, content: JsonOutput.toJson([number: 42, title: j.title, base:[ref:j.base], head:[ref:j.head]]))
      }

      if (url.contains('/api/v1/repos/acme/my-repo/pulls') && method == 'GET') {
        // list PRs by state + pagination; return two items on page 1, none on page 2
        def uri = new URI(url)
        def params = uri.query?.split('&')?.collectEntries { it.split('=') as List } ?: [:]
        def page = (params.page ?: '1') as int
        def state = params.state ?: 'open'
        if (page == 1) {
          def list = [
            [number: 1, title:'A', state: state, merged:false, base:[ref:'main'], head:[ref:'feature/foo']],
            [number: 2, title:'B', state: state, merged:true,  base:[ref:'main'], head:[ref:'feature/old']]
          ]
          return new FakeResponse(status: 200, content: JsonOutput.toJson(list))
        } else {
          return new FakeResponse(status: 200, content: JsonOutput.toJson([]))
        }
      }

      if (url.endsWith('/api/v1/repos/acme/my-repo/issues/1/comments') && method == 'POST') {
        def j = new JsonSlurper().parseText(body)
        assert j.body
        return new FakeResponse(status: 201, content: JsonOutput.toJson([id: 555, body: j.body]))
      }

      if (url.endsWith('/api/v1/repos/acme/my-repo/statuses/abcd1234') && method == 'POST') {
        // Accept any status mapping; echo back
        def j = new JsonSlurper().parseText(body)
        return new FakeResponse(status: 201, content: JsonOutput.toJson([state: j.state, context: j.context, target_url: j.target_url]))
      }

      if (url.endsWith('/api/v1/repos/acme/my-repo/commits/abcd1234/pull') && method == 'GET') {
        // Return one PR linked to this commit
        def list = [[number: 1, head:[ref:'feature/foo'], base:[ref:'main']]]
        return new FakeResponse(status: 200, content: JsonOutput.toJson(list))
      }

      if (url.endsWith('/api/v1/repos/acme/my-repo/pulls/1') && method == 'GET') {
        def pr = [number:1, base:[ref:'main'], head:[ref:'feature/foo']]
        return new FakeResponse(status: 200, content: JsonOutput.toJson(pr))
      }

      if (url.contains('/api/v1/repos/acme/my-repo/compare/')) {
        // Return 3 commits for compare (we'll paginate in client)
        def cmp = [ commits: [
          [id:'c1'], [id:'c2'], [id:'c3']
        ]]
        return new FakeResponse(status: 200, content: JsonOutput.toJson(cmp))
      }

      // Default: 200 OK empty
      return new FakeResponse(status: 200, content: '')
    }
  }

  // If your code references ILogger, supply a no-op or pass null; adapter does not currently log.
  interface ILogger {
    void info(String m); void warn(String m); void error(String m)
  }
  static class NopLogger implements ILogger {
    void info(String m){}; void warn(String m){}; void error(String m){}
  }

  // --- Tests ------------------------------------------------------------------

  def 'createPullRequest should POST PR with base/head/assignees'() {
    given:
      def script = new FakeScript()
      script.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(script, script.env.FORGEJO_URL, 'acme/my-repo', 'cred-id', null)

    when:
      def json = svc.createPullRequest('acme/my-repo', 'feature/foo', 'main', 'feat: foo', 'desc', ['alice','bob'])
      def pr = new JsonSlurper().parseText(json)

    then:
      pr.title == 'feat: foo'
      pr.head.ref == 'feature/foo'
      pr.base.ref == 'main'
      script.last.httpMode == 'POST'
      script.last.url.endsWith('/api/v1/repos/acme/my-repo/pulls')
      (script.last.customHeaders*.value).find { it.startsWith('token ') }
  }

  def 'postComment should POST to issue comments'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when:
      svc.postComment('acme/my-repo', 1, 'LGTM')

    then:
      s.last.httpMode == 'POST'
      s.last.url.endsWith('/api/v1/repos/acme/my-repo/issues/1/comments')
      new JsonSlurper().parseText(s.last.requestBody).body == 'LGTM'
  }

  def 'setBuildStatus maps states correctly'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when: 'INPROGRESS should map to pending'
      svc.setBuildStatus('http://ci/build/1', 'abcd1234', 'INPROGRESS', 'ci/build')
    then:
      new JsonSlurper().parseText(s.last.requestBody).state == 'pending'

    when: 'SUCCESS should map to success'
      svc.setBuildStatus('http://ci/build/2', 'abcd1234', 'SUCCESS', 'ci/build')
    then:
      new JsonSlurper().parseText(s.last.requestBody).state == 'success'
  }

  def 'postTag should delete existing tag when force=true and then create it'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when:
      svc.postTag('acme/my-repo', 'main', 'v2.0.0', true, 'release 2')

    then:
      // Expect GET (list tags), DELETE (old tag), POST (new tag)
      def methods = s.calls*.httpMode
      methods.count('GET') >= 1
      methods.contains('DELETE')
      methods.contains('POST')
  }

  def 'getDefaultBranch should return main'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    expect:
      svc.getDefaultBranch('acme/my-repo') == 'main'
  }

  def 'findRepoBranches filters by substring'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when:
      def res = svc.findRepoBranches('acme/my-repo', 'feature/')

    then:
      (res.branches*.name) == ['feature/foo']
  }

  def 'getPullRequests should request open state'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when:
      def json = svc.getPullRequests('acme/my-repo', 'OPEN')
      def list = new JsonSlurper().parseText(json) as List

    then:
      list*.number == [1,2]
      s.last.url.contains('state=open')
  }

  def 'findPullRequest should locate PR by head branch'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when:
      def pr = svc.findPullRequest('acme/my-repo', 'feature/foo', 'OPEN')

    then:
      pr.number == 1
      pr.head.ref == 'feature/foo'
  }

  def 'getPullRequestsForCommit should return linked PRs'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when:
      def json = svc.getPullRequestsForCommit('acme/my-repo', 'abcd1234')
      def list = new JsonSlurper().parseText(json) as List

    then:
      list.size() == 1
      list[0].number == 1
  }

  def 'getCommmitsForPullRequest should use compare base...head and paginate client-side'() {
    given:
      def s = new FakeScript();
      s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when: 'page 1, perPage 2'
      def res1 = svc.getCommmitsForPullRequest(null, 'acme/my-repo', 1, 2, 1)
    then:
      res1.values*.id == ['c1','c2']
      res1.isLastPage == false
      res1.nextPageStart == 2

    when: 'page 2'
      def res2 = svc.getCommmitsForPullRequest(null, 'acme/my-repo', 1, 2, 2)
    then:
      res2.values*.id == ['c3']
      res2.isLastPage == true
  }

  def 'createCodeInsightReport should delegate to setBuildStatus'() {
    given:
      def s = new FakeScript(); s.env.FORGEJO_URL = 'https://forgejo.example.com'
      def svc = new ScmForgejoService(s, s.env.FORGEJO_URL, 'acme/my-repo', 'cred', null)

    when:
      svc.createCodeInsightReport([name:'quality/gate', state:'SUCCESS', targetUrl:'http://ci/123'], 'acme/my-repo', 'abcd1234')

    then:
      s.last.url.endsWith('/api/v1/repos/acme/my-repo/statuses/abcd1234')
      new JsonSlurper().parseText(s.last.requestBody).context == 'quality/gate'
  }
}
