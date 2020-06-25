package org.ods.orchestration.service.leva

import groovy.json.JsonSlurperClassic
import org.ods.util.IPipelineSteps

import java.nio.file.Paths

class ProjectDataBitbucketRepository {

    static final String BASE_DIR = 'projectIssues'

    private IPipelineSteps steps

    ProjectDataBitbucketRepository(IPipelineSteps steps) {
        this.steps = steps
    }

    void save(Map jiraData, String version) {
        def jsonOut = this.steps.readJSON( text: groovy.json.JsonOutput.toJson(jiraData))
        this.steps.writeJSON(file: "${BASE_DIR}/${version}.json", json: jsonOut, pretty: 2)
        println("Saved data to ${BASE_DIR}/${version}.json")
    }

    Map loadVersionSnapshot(String savedVersion) {
        def savedData
        def file = Paths.get(this.steps.env.WORKSPACE, BASE_DIR,
            "${savedVersion}.json").toFile()
        if (!file.exists()) {
            savedData = this.steps.readFile(file: "${BASE_DIR}/${savedVersion}.json")
        } else {
            savedData = file.text
        }

        def data = [:]
        if (!savedData) {
            throw new RuntimeException(
                "Error: unable to load document chapters. File 'docs/${documentType}.yaml' could not be read."
            )
        } else {
            data =new JsonSlurperClassic().parseText(savedData) ?: [:]
        }

        return data
    }
}
