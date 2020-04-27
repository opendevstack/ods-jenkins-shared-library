package org.ods.orchestration.service

import java.nio.file.Paths

import spock.lang.*

import util.*

class LeVADocumentChaptersFileServiceSpec extends SpecHelper {

    def "get document chapter data"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new LeVADocumentChaptersFileService(steps)

        def type = "myType"

        def levaPath = Paths.get(steps.env.WORKSPACE, LeVADocumentChaptersFileService.DOCUMENT_CHAPTERS_BASE_DIR)
        levaPath.toFile().mkdirs()

        def levaFile = Paths.get(levaPath.toString(), "${type}.yaml")
        levaFile << """
        - number: 1
          heading: Section 1
          content: Content 1
        - number: 2
          heading: Section 2
          content: Content 2
        - number: 2.1
          heading: Section 2.1
          content: Content 2.1
        """

        when:
        def result = service.getDocumentChapterData(type)

        then:
        result."sec1".number == "1"
        result."sec1".heading == "Section 1"
        result."sec1".content == "Content 1"

        result."sec2".number == "2"
        result."sec2".heading == "Section 2"
        result."sec2".content == "Content 2"

        result."sec2s1".number == "2.1"
        result."sec2s1".heading == "Section 2.1"
        result."sec2s1".content == "Content 2.1"

        cleanup:
        levaPath.deleteDir()
    }

    def "get document chapter data with invalid documentType"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = new LeVADocumentChaptersFileService(steps)

        when:
        service.getDocumentChapterData(null)

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load document chapters. 'documentType' is undefined."

        when:
        service.getDocumentChapterData("")

        then:
        e = thrown(IllegalArgumentException)
        e.message == "Error: unable to load document chapters. 'documentType' is undefined."

        when:
        def type = "myType"
        service.getDocumentChapterData(type)

        then:
        e = thrown(RuntimeException)
        e.message == "Error: unable to load document chapters. File 'docs/${type}.yaml' could not be read."
    }
}
