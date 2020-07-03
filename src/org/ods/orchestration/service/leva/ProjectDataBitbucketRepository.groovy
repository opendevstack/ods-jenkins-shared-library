package org.ods.orchestration.service.leva

import groovy.json.JsonSlurperClassic
import org.ods.util.IPipelineSteps


class ProjectDataBitbucketRepository {

    static final String BASE_DIR = 'projectData'

    private IPipelineSteps steps

    ProjectDataBitbucketRepository(IPipelineSteps steps) {
        this.steps = steps
    }

    void save(Map jiraData, String version) {
        def jsonOut = this.steps.readJSON( text: groovy.json.JsonOutput.toJson(jiraData))
        this.steps.writeJSON(file: "${BASE_DIR}/${version}.json", json: jsonOut, pretty: 2)
    }

    Map loadVersionSnapshot(String savedVersion) {
        def savedData =  this.steps.readFile(file: "${BASE_DIR}/${savedVersion}.json")
        def data = [:]
        if (savedData) {
            data = new JsonSlurperClassic().parseText(savedData) ?: [:]
        } else {
            throw new RuntimeException(
                'Error: unable to load saved information prom the previous version. ' +
                    "File '${BASE_DIR}/${savedVersion}.json' could not be read."
            )
        }
        return data
    }

}
