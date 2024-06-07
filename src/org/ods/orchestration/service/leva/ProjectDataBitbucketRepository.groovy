package org.ods.orchestration.service.leva

import groovy.json.JsonOutput
import groovy.json.JsonSlurperClassic
import org.ods.util.IPipelineSteps

import java.nio.file.NoSuchFileException

class ProjectDataBitbucketRepository {

    static final String BASE_DIR = 'projectData'

    private final IPipelineSteps steps

    ProjectDataBitbucketRepository(IPipelineSteps steps) {
        this.steps = steps
    }

    String save(Object jiraData, String version) {
        def fileName = "${BASE_DIR}/${version}.json"
        steps.writeFile(
            file: fileName,
            text: JsonOutput.prettyPrint(JsonOutput.toJson(jiraData))
        )
        return fileName
    }

    Object loadFile(String fileName) {
        try {
            def savedData =  this.steps.readFile(file: "${BASE_DIR}/${fileName}.json")
            def data = [:]
            if (savedData) {
                data = new JsonSlurperClassic().parseText(savedData) ?: [:]
            } else {
                throw new RuntimeException(
                    'Error: unable to load saved information from the previous version. ' +
                        "File '${BASE_DIR}/${fileName}.json' could not be read."
                )
            }
            return data
        } catch (NoSuchFileException e) {
            throw new NoSuchFileException("File '${BASE_DIR}/${fileName}.json' is expected to be inside the release " +
                'manager repository but was not found and thus, document history cannot be build. If you come from ' +
                'and old ODS version, create one for each document to use the automated document history feature.')
        }
    }

}
