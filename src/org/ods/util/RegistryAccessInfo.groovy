package org.ods.util

import groovy.transform.TypeChecked
import com.cloudbees.groovy.cps.NonCPS

// RegistryAccessInfo holds configuration of a container image registry.
@TypeChecked
class RegistryAccessInfo {

    // host is the hostname of the image registry.
    // Example: default-route-openshift-image-registry.example.com
    String host

    // username is the username of a user with access to the image registry
    // located at given host.
    // Example: jenkins
    String username

    // password the password of a user with access to the image registry
    // located at given host.
    // Example: eyJhbGc...GGxf0g
    String password

    String getCredentials() {
        "${username}:${password}"
    }

    @NonCPS
    Map<String, String> toMap() {
        [
            host: host,
            username: username,
            password: password,
        ]
    }

    @NonCPS
    String toString() {
        toMap().toMapString()
    }

}
