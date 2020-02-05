package org.ods.util

import java.nio.file.Files
import java.nio.file.Paths

import org.apache.pdfbox.pdmodel.PDDocument

import spock.lang.*

import static util.FixtureHelper.*

import util.*

class PDFUtilSpec extends SpecHelper {

    def "add watermark text"() {
        given:
        def util = new PDFUtil()

        def pdfFile = getResource("Test-1.pdf")
        def text = "myWatermark"

        when:
        def result = util.addWatermarkText(pdfFile.bytes, text)

        then:
        def doc = PDDocument.load(result)
        doc.getNumberOfPages() == 1
        doc.getPage(0).getContents().text.contains(text)
    }

    def "convert from Microsoft Word document"() {
        given:
        def util = new PDFUtil()

        def docFile = getResource("Test.docx")

        when:
        def result = util.convertFromWordDoc(docFile)

        then:
        PDDocument.load(result).getNumberOfPages() == 1
    }

    def "merge documents"() {
        given:
        def util = new PDFUtil()

        def docFile1 = getResource("Test-1.pdf")
        def docFile2 = getResource("Test-2.pdf")

        when:
        def result = util.merge([docFile1.bytes, docFile2.bytes])

        then:
        new String(result).startsWith("%PDF-1.4\n")

        then:
        PDDocument.load(result).getNumberOfPages() == 2
    }
}
