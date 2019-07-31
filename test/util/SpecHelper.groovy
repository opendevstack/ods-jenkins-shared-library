package util

import com.github.tomakehurst.wiremock.*
import com.github.tomakehurst.wiremock.client.*

import java.net.URI

import org.junit.Rule
import org.junit.contrib.java.lang.system.EnvironmentVariables

import spock.lang.*

import static com.github.tomakehurst.wiremock.client.WireMock.*
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options

class SpecHelper extends Specification {
    @Shared WireMockServer wireMockServer

    @Rule
    EnvironmentVariables env = new EnvironmentVariables()

    def cleanup() {
        stopWireMockServer()
    }

    // Get a test resource file by name
    def File getResource(String name) {
        new File(getClass().getClassLoader().getResource(name).getFile())
    }

    // Starts and configures a WireMock server
    def WireMockServer startWireMockServer(URI uri) {
        this.wireMockServer = new WireMockServer(
            options().port(uri.getPort())
        )

        this.wireMockServer.start()
        WireMock.configureFor(uri.getPort())

        return this.wireMockServer
    }

    // Stops a WireMock server
    def void stopWireMockServer() {
        if (this.wireMockServer) {
            this.wireMockServer.stop()
            this.wireMockServer = null
        }
    }
}