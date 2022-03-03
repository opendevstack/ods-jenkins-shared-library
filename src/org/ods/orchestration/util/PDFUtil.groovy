package org.ods.orchestration.util

@Grab('fr.opensagres.xdocreport:fr.opensagres.poi.xwpf.converter.core:2.0.2')
@Grab('fr.opensagres.xdocreport:fr.opensagres.poi.xwpf.converter.pdf:2.0.2')
@Grab('org.apache.pdfbox:pdfbox:2.0.17')
@Grab('org.apache.poi:poi:4.0.1')

import com.cloudbees.groovy.cps.NonCPS

import fr.opensagres.poi.xwpf.converter.pdf.PdfConverter
import fr.opensagres.poi.xwpf.converter.pdf.PdfOptions

import java.nio.file.Files

import org.apache.commons.io.IOUtils
import org.apache.pdfbox.multipdf.PDFMergerUtility
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.blend.BlendMode
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import org.apache.pdfbox.util.Matrix
import org.apache.poi.xwpf.usermodel.XWPFDocument

import java.nio.file.Paths

@SuppressWarnings(['JavaIoPackageAccess', 'LineLength', 'UnnecessaryObjectReferences'])
class PDFUtil {

    @NonCPS
    byte[] addWatermarkText(byte[] file, String text) {
        def result

        PDDocument doc
        try {
            doc = PDDocument.load(file)
            doc.getPages().each { page ->
                def font = PDType1Font.HELVETICA
                addWatermarkTextToPage(doc, page, font, text)
            }

            def os = new ByteArrayOutputStream()
            doc.save(os)
            result = os.toByteArray()
        } catch (e) {
            throw new RuntimeException("Error: unable to add watermark to PDF document: ${e.message}").initCause(e)
        } finally {
            if (doc) {
                doc.close()
            }
        }

        return result
    }

    @NonCPS
    byte[] convertFromMarkdown(File wordDoc, Boolean landscape = false) {
        def result

        try {
            def markdownContent = IOUtils.toString(new FileInputStream(wordDoc), 'UTF-8')
            result = new MarkdownUtil().toPDF(markdownContent, landscape)
        } catch (e) {
            throw new RuntimeException("Error: unable to convert Markdown document to PDF: ${e.message}").initCause(e)
        }

        return result
    }

    @NonCPS
    byte[] convertFromMarkdown(String markdownContent, Boolean landscape = false) {
        def result

        try {
            result = new MarkdownUtil().toPDF(markdownContent, landscape)
        } catch (e) {
            throw new RuntimeException("Error: unable to convert Markdown document to PDF: ${e.message}").initCause(e)
        }

        return result
    }

    @NonCPS
    byte[] convertFromWordDoc(File wordDoc) {
        def result

        XWPFDocument doc
        try {
            def is = new FileInputStream(wordDoc)
            doc = new XWPFDocument(is)

            def options = PdfOptions.create()
            def os = new ByteArrayOutputStream()
            PdfConverter.getInstance().convert(doc, os, options)

            result = os.toByteArray()
        } catch (e) {
            throw new RuntimeException("Error: unable to convert Word document to PDF: ${e.message}").initCause(e)
        } finally {
            if (doc) {
                doc.close()
            }
        }

        return result
    }

    @NonCPS
    byte[] merge(String workspacePath, List<byte[]> files) {
        def result
        File tmp
        try {
            tmp = Files.createTempFile(Paths.get(workspacePath), "merge", "pdf").toFile()
            def merger = new PDFMergerUtility()
            merger.setDestinationStream(new FileOutputStream(tmp))

            files.each { file ->
                merger.addSource(new ByteArrayInputStream(file))
            }

            merger.mergeDocuments()
            result = tmp.bytes
        } catch (e) {
            throw new RuntimeException("Error: unable to merge PDF documents: ${e.message}").initCause(e)
        } finally {
            tmp?.delete()
        }

        return result
    }

    @NonCPS
    // Courtesy of https://svn.apache.org/viewvc/pdfbox/trunk/examples/src/main/java/org/apache/pdfbox/examples/util/AddWatermarkText.java
    private addWatermarkTextToPage(PDDocument doc, PDPage page, PDFont font, String text) {
        def cs
        try {
            cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true)

            def fontHeight = 100 // arbitrary for short text
            def width = page.getMediaBox().getWidth()
            def height = page.getMediaBox().getHeight()
            float stringWidth = font.getStringWidth(text) / 1000 * fontHeight
            float diagonalLength = (float) Math.sqrt(width * width + height * height)
            float angle = (float) Math.atan2(height, width)
            float x = (diagonalLength - stringWidth) / 2 // "horizontal" position in rotated world
            float y = -fontHeight / 4 // 4 is a trial-and-error thing, this lowers the text a bit
            cs.transform(Matrix.getRotateInstance(angle, 0, 0))
            cs.setFont(font, fontHeight)

            def gs = new PDExtendedGraphicsState()
            gs.setNonStrokingAlphaConstant(0.05f)
            gs.setStrokingAlphaConstant(0.05f)
            gs.setBlendMode(BlendMode.MULTIPLY)
            gs.setLineWidth(3f)
            cs.setGraphicsStateParameters(gs)

            // some API weirdness here. When int, range is 0..255.
            // when float, this would be 0..1f
            cs.setNonStrokingColor(0, 0, 0)
            cs.setStrokingColor(0, 0, 0)

            cs.beginText()
            cs.newLineAtOffset(x, y)
            cs.showText(text)
            cs.endText()
        } finally {
            cs.close()
        }
    }

}
