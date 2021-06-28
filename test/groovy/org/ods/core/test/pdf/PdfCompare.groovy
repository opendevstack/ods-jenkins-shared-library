package org.ods.core.test.pdf

import groovy.util.logging.Slf4j
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer

import java.awt.image.BufferedImage

@Slf4j
class PdfCompare {
    private static final String PDF = ".pdf"
    private static final String DIFF = "_diff.png"
    private static final int DPI = 300

    private final String imageDestinationPath
    private int endPage = -1

    PdfCompare(String imageDestinationPath){
        this.imageDestinationPath = imageDestinationPath
    }

    /**
     * This method reads each page of a given pdf, converts to image compare.
     *
     * @param actualPdf
     * @param expectedPdf
     * @return
     * @throws IOException
     */
    private boolean compare(String actualPdf, String expectedPdf) throws IOException{
        log.debug("actualPdf: ${actualPdf}")
        log.debug("expectedPdf: ${expectedPdf}")

        endPage = this.getPageCount(actualPdf)
        if(endPage != this.getPageCount(expectedPdf)){
            log.debug("files page counts do not match - returning false")
            return false
        }
        return comparePageByPage(actualPdf, expectedPdf, endPage)
    }

    private Boolean comparePageByPage(String file1, String file2, int endPage) {
        def result = true
        PDDocument doc1 = null
        PDDocument doc2 = null
        try {
            doc1 = PDDocument.load(new File(file1))
            doc2 = PDDocument.load(new File(file2))
            PDFRenderer pdfRenderer1 = new PDFRenderer(doc1)
            PDFRenderer pdfRenderer2 = new PDFRenderer(doc2)

            for (int iPage = 0; iPage < endPage; iPage++) {
                log.trace("Comparing Page No : " + (iPage + 1))
                BufferedImage image1 = pdfRenderer1.renderImageWithDPI(iPage, DPI, ImageType.RGB)
                BufferedImage image2 = pdfRenderer2.renderImageWithDPI(iPage, DPI, ImageType.RGB)
                result = result & ImageCompare.compareAndHighlight(image1, image2, errorFileName(file1, iPage))
            }
        } catch (Exception e) {
            throw new RuntimeException("Error comparing files", e)
        } finally {
            doc1.close()
            doc2.close()
        }

        return result
    }

    private String errorFileName(String file1, int iPage) {
        String fileName = new File(file1).getName().replace(PDF, "_") + (iPage + 1)
        fileName = this.imageDestinationPath + File.separator + fileName + DIFF
        fileName
    }

    private int getPageCount(String file) throws IOException{
        PDDocument doc = PDDocument.load(new File(file))
        int pageCount = doc.getNumberOfPages()
        doc.close()

        log.debug("file : ${file} - pageCount :${pageCount}")
        return pageCount
    }
}
