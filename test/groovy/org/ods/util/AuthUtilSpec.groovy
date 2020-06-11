package org.ods.util

import spock.lang.Specification
import spock.lang.Unroll

class AuthUtilSpec extends Specification {

    @Unroll
    def "check gitCredentialLines works for #url, #username, #password"() {
        expect:
        AuthUtil.gitCredentialLines(url, username, password) == expected
        where:
        url | username | password | expected
        "https://hi:pw@git.com" | "one@example.com" | "123 \u00a3" | ["protocol=https", "host=git.com", "username=one@example.com", "password=123 £", ""]
        "https://hi:pw@git.com:8842" | "one@example.com" | "123 \u00a3" | ["protocol=https", "host=git.com:8842", "username=one@example.com", "password=123 £", ""]
    }

    @Unroll
    def "auth value is as expected for Basic with #username, #password"() {
        expect:
        AuthUtil.headerValue(AuthUtil.SCHEME_BASIC, username, password) == expected
        where:
        username | password | expected
//      first two form https://tools.ietf.org/html/rfc7617#page-3
        "Aladdin" | "open sesame" | "Basic QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
        "test" | "123\u00a3" | "Basic dGVzdDoxMjPCow=="
        "one@example.com" | "123" | "Basic b25lQGV4YW1wbGUuY29tOjEyMw=="
        "one@example.com" | "" | "Basic b25lQGV4YW1wbGUuY29tOg=="
        "two" | '''@!|\\*$foo''' | "Basic dHdvOkAhfFwqJGZvbw=="
        "three" | "'" | "Basic dGhyZWU6Jw=="
        "four" | '"hi"' | "Basic Zm91cjoiaGki"
    }

    @Unroll
    def "basic auth header is #expected" () {
        expect:
        AuthUtil.header(AuthUtil.SCHEME_BASIC, username, password) == expected
        where:
        username | password | expected
        "test" | "123\u00a3" | "Authorization: Basic dGVzdDoxMjPCow=="
    }

    @Unroll
    def "bearer auth header is #expected" () {
        expect:
        AuthUtil.header(AuthUtil.SCHEME_BEARER, username, password) == expected
        where:
        username | password | expected
        "test" | "123\u00a3" | "Authorization: Bearer dGVzdDoxMjPCow=="
    }


    @Unroll
    def "custom auth header is #expected" () {
        expect:
        AuthUtil.header("Custom", username, password) == expected
        where:
        username | password | expected
        "test" | "123\u00a3" | "Authorization: Custom dGVzdDoxMjPCow=="
    }

    @Unroll
    def "alternative custom auth header in lowercase is #expected" () {
        expect:
        "authorization: Custom ${AuthUtil.basicSchemeAuthValue(username, password)}" == expected
        where:
        username | password | expected
        "test" | "123\u00a3" | "authorization: Custom dGVzdDoxMjPCow=="
    }
}
