
package org.ods.orchestration.util

import java.nio.file.Files
import java.nio.file.Paths

import org.apache.pdfbox.pdmodel.PDDocument

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class MarkdownUtilSpec extends SpecHelper {

    def "convert from mardkdown document"() {
        given:
        def util = new MarkdownUtil()

        def titleString = "This is a Markdown document"
        def content = "# ${titleString}"

        when:
        def result = util.toHtml(content)

        then:
        result.contains("<h1>${titleString}</h1>")
    }

    def "Converts a markdown table"() {
        given:
        def util = new MarkdownUtil()

        def content = """\
        This is a text and contains a table

        header1|header2
        ---|---
        col1|col2

        """.stripIndent()

        when:
        def result = util.toHtml(content)

        then:
        result.contains("<table>")
        result.contains("</table>")

    }

    def "Converts to PDF"() {
        given:
        def util = new MarkdownUtil()

        def content = """
        # Document 1 test

        header1|header2
        ---|---
        col1|col2
        col1|col2
        col1|col2
        """

        when:
        def result = util.toPDF(content, false)

        then:
        def doc = PDDocument.load(result)
        doc.getNumberOfPages() == 1
        def page = doc.getPage(0)
        def pageSize = page.getMediaBox()
        def degree = page.getRotation()
        def isLandscape = (( pageSize.getWidth() > pageSize.getHeight()) ||(degree==90)||(degree==270))
        isLandscape == false
        doc.close()
    }

    def "Converts to PDF in landscape"() {
        given:
        def util = new MarkdownUtil()

        def content = """
        # Document 1 test
        """

        when:
        def result = util.toPDF(content, true)

        then:
        def doc = PDDocument.load(result)
        doc.getNumberOfPages() == 1
        def page = doc.getPage(0)
        def pageSize = page.getMediaBox()
        def degree = page.getRotation()
        def isLandscape = (( pageSize.getWidth() > pageSize.getHeight()) ||(degree==90)||(degree==270))
        isLandscape == true
        doc.close()
    }
}
