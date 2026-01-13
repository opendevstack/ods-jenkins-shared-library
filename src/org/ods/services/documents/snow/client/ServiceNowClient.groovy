package org.ods.services.documents.snow.client

import com.cloudbees.groovy.cps.NonCPS;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.stream.Collectors

class ServiceNowClient {
    private static final Logger log = LoggerFactory.getLogger(ServiceNowClient)

    ServiceNowConfig config

    ServiceNowClient(ServiceNowConfig config) {
        this.config = config
    }

    @NonCPS
    String getAccessToken() {
        log.debug("Getting Access Token")

        def url = "https://${config.instance}/oauth_token.do"
        def body = [
        grant_type    : "password",
                client_id     : config.clientId,
                client_secret : config.clientSecret,
                username      : config.username,
                password      : config.password
        ]

        def response = new URL(url).openConnection()
        response.setRequestMethod("POST")
        response.setDoOutput(true)
        response.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        response.outputStream.withWriter { writer ->
                writer << body.collect { k, v -> "${k}=${URLEncoder.encode(v, "UTF-8")}" }.join("&")
        }

        def json = new groovy.json.JsonSlurper().parse(response.inputStream)
        log.debug("Access Token Response: {}", json)
        def accessToken = json.access_token

        log.debug("Got access token: {}", accessToken)

        return accessToken
    }

    @NonCPS
    List<Map> getBusinessApplicationRoles(String accessToken) {
        log.debug("Getting business application roles, token: {}", accessToken)

        def url = "https://${config.instance}/api/now/table/x_boigh_business_a_business_application_roles" +
                "?business_application_name=${config.businessApplication}" +
                "&sysparm_fields=${config.sysparmRalFields}" +
                "&sysparm_limit=${config.sysparmRalLimit}"

        def connection = new URL(url).openConnection()
        connection.setRequestProperty("Authorization", "Bearer ${accessToken}")
        connection.setRequestProperty("Accept", "application/json")

        def json = new groovy.json.JsonSlurper().parse(connection.inputStream)

        log.debug("Bussiness application roles response: {}", json)
        return json.result
    }

    @NonCPS
    Map getUserDetails(String accessToken, String sysId) {
        log.debug("Getting user details. AccessToken: {}, sysId: {}", accessToken, sysId)

        def query = "sys_id=${sysId}"
        def url = "https://${config.instance}/api/now/table/sys_user" +
                "?sysparm_query=${query}" +
                "&sysparm_fields=${config.sysparmUserFields}" +
                "&sysparm_limit=${config.sysparmUserLimit}"

        def connection = new URL(url).openConnection()
        connection.setRequestProperty("Authorization", "Bearer ${accessToken}")
        connection.setRequestProperty("Accept", "application/json")

        def json = new groovy.json.JsonSlurper().parse(connection.inputStream)

        log.debug("User Details response: {}", json)

        return json.result[0]
    }

    @NonCPS
    String getUserEmail(String token, List<Map> businessApplicationRoles, String role) {
        return businessApplicationRoles.findAll { it.role == role }
            .collect { getUserEmailByRoleInformation(token, it)}.find() ?: ''
    }

    @NonCPS
    Map<String, String> getUserEmails(String token, List<Map> businessApplicationRoles, Set<String> roles) {
        return roles.stream()
            .collect(Collectors.toMap(
                { role -> role },
                { role -> getUserEmail(token, businessApplicationRoles, role as String) }
            ))
    }

    @NonCPS
    private String getUserEmailByRoleInformation(String token, Map roleInformation) {
        def sysId = roleInformation.user.value
        def userDetails = getUserDetails(token, sysId)

        return userDetails?.email
    }
}

