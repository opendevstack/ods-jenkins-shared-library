package org.ods.orchestration.service

@Grab('org.yaml:snakeyaml:1.24')

import java.nio.file.Paths

import org.ods.util.IPipelineSteps
import org.yaml.snakeyaml.Yaml

class LeVADocumentChaptersFileService {

    static final String DOCUMENT_CHAPTERS_BASE_DIR = 'docs'

    private IPipelineSteps steps

    LeVADocumentChaptersFileService(IPipelineSteps steps) {
        this.steps = steps
    }

    Map getDocumentChapterData(String documentType) {
        if (!documentType?.trim()) {
            throw new IllegalArgumentException(
                "Error: unable to load document chapters. 'documentType' is undefined."
            )
        }

        def String yamlText
        def file = Paths.get(this.steps.env.WORKSPACE, DOCUMENT_CHAPTERS_BASE_DIR,
            "${documentType}.yaml").toFile()
        if (!file.exists()) {
            yamlText = this.steps.readFile(file: "docs/${documentType}.yaml")
        } else {
            yamlText = file.text
        }

        def data = [:]
        if (!yamlText) {
            throw new RuntimeException(
                "Error: unable to load document chapters. File 'docs/${documentType}.yaml' could not be read."
            )
        } else {
            data = new Yaml().load(yamlText) ?: [:]
        }

        return data.collectEntries { chapter ->
            def number = chapter.number.toString()
            chapter.number = number
            chapter.content = chapter.content ?: ''
            chapter['status'] = 'done'
            [ "sec${number.replaceAll(/\./, 's')}".toString(), chapter ]
        }
    }

}
