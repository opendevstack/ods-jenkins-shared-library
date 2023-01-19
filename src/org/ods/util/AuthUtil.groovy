package org.ods.util

import com.cloudbees.groovy.cps.NonCPS
import groovy.transform.TypeChecked
import org.apache.http.client.utils.URIBuilder

import java.nio.charset.StandardCharsets

@TypeChecked
class AuthUtil {

    public static final String HEADER_AUTHORIZATION = 'Authorization'
    public static final String SCHEME_BASIC = 'Basic'
    public static final String SCHEME_BEARER = 'Bearer'

    @NonCPS
    static String base64(String str) {
        Base64.getEncoder().encodeToString((str).getBytes(StandardCharsets.UTF_8))
    }

    @NonCPS
    static String basicSchemeAuthValue(String username, String password) {
        base64(username + ':' + password)
    }

    @NonCPS
    static String header(String scheme, String username, String password) {
        "${HEADER_AUTHORIZATION}: ${headerValue(scheme, username, password)}"
    }

    @NonCPS
    static String headerValue(String scheme, String username, String password) {
        "${scheme} ${basicSchemeAuthValue(username, password)}"
    }

    @NonCPS
    // useful to feed into `git credential-store store`
    static String[] gitCredentialLines(String url, String username, String password) {
        String proto = url.startsWith('http://') ? 'http' :
            (url.startsWith('https://') ? 'https' : null)
        if (proto == null) {
            throw new IllegalArgumentException("Protocol of ${url} must be 'http' or 'https'")
        }

        URI baseURL
        try {
            baseURL = new URIBuilder(url).build()
        } catch (e) {
            throw new IllegalArgumentException("'${url}' is not a valid URI."
            ).initCause(e)
        }
        String host = baseURL.host
        String port = baseURL.port > -1 ? ":${baseURL.port}" : ''

        [
            "protocol=${proto}",
            "host=${host}${port}",
            "username=${username}",
            "password=${password}",
            '',
        ] as String[]
    }

}
