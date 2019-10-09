package org.ods.service

@Grab('org.yaml:snakeyaml:1.24')

import java.nio.file.Paths

import org.ods.util.IPipelineSteps
import org.yaml.snakeyaml.Yaml

class LeVaDocumentChaptersFileService {

    static final String DOCUMENT_CHAPTERS_BASE_DIR = "docs"

    private IPipelineSteps steps

    LeVaDocumentChaptersFileService(IPipelineSteps steps) {
        this.steps = steps
    }

    Map getDocumentChapterData(String documentType) {
        if (!documentType?.trim()) {
            throw new IllegalArgumentException("Error: unable to load document chapters. 'documentType' is undefined.")
        }

        def file = Paths.get(this.steps.env.WORKSPACE, DOCUMENT_CHAPTERS_BASE_DIR, "${documentType}.yaml").toFile()
        if (!file.exists()) {
            throw new RuntimeException("Error: unable to load document chapters. File '${file.toString()}' does not exist.")
        }

        def data = new Yaml().load(file.text) ?: [:]
        return data.collectEntries { chapter ->
            def number = chapter.number.toString()
            chapter.number = number
            chapter.content = chapter.content ?: ""
            [ "sec${number.replaceAll(/\./, "s")}".toString(), chapter ]
        }
    }
}
