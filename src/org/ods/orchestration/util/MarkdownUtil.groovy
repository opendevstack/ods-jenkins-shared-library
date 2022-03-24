package org.ods.orchestration.util

@Grab('com.vladsch.flexmark:flexmark-all:0.60.2')

import com.cloudbees.groovy.cps.NonCPS

import com.vladsch.flexmark.html.HtmlRenderer
import com.vladsch.flexmark.parser.Parser
import com.vladsch.flexmark.parser.ParserEmulationProfile
import com.vladsch.flexmark.util.data.MutableDataSet
import com.vladsch.flexmark.pdf.converter.PdfConverterExtension
import com.vladsch.flexmark.ext.tables.TablesExtension

class MarkdownUtil {

    private final String CSS_STYLE = """
    <style>
        @page landscapeA4 {
            size: A4 landscape;
        }
        * {
            font-family: Arial, Verdana, sans-serif;
            line-height: 1em;
        }
        body {
            margin: 0;
            width: 100%;
        }
        .landscape {
            page: landscapeA4;
        }
        h1 {
            font-size: 2em;
        }
        h1 span {
            display: inline-block;
            margin-right: 2em;
        }
        h2 {
            font-size: 1.6em;
            page-break-after: avoid;
            text-transform: uppercase;
        }
        h2 span {
            display: inline-block;
            margin-right: 1.65em;
        }
        h3 {
            text-transform: uppercase;
        }
        h3 span {
            display: inline-block;
            margin-right: 1.5em;
        }
        p {
            margin: 1em 0;
        }
        table {
            table-layout: auto;
            width: 130%;
            border-collapse: collapse;
            border-spacing: 0;
            white-space: normal;
            margin: 2em 0;
            page-break-inside: avoid;
        }
        table, table th, table td {
            border: 1px solid black;
        }
        table th, table td {
            height: 2em;
            white-space: normal;
            padding: 0.5em 0.5em;
        }
        table th, table td.header {
            font-weight: bold;
            text-align: left;
            background-color: #e6e6e6;
        }
        .center {
            text-align: center;
        }
        .right {
            text-align: right;
        }
    </style>
    """
    private def OPTIONS = new MutableDataSet()
       .set(Parser.EXTENSIONS, Arrays.asList(TablesExtension.create()))
       .setFrom(ParserEmulationProfile.MARKDOWN)

    @NonCPS
    String toHtml(String markdownText) {
        def parser = Parser.builder(this.OPTIONS).build()
        def renderer = HtmlRenderer.builder(this.OPTIONS).build()
        def document = parser.parse(markdownText)
        return renderer.render(document)
    }

    @NonCPS
    byte[] toPDF(String markdownText, Boolean landscape = false) {
        def parsedMarkdown = this.toHtml(markdownText)
        def landscapeContent = (landscape) ? "class=\"landscape\"" : ""
        def html = "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">\n" +
                this.CSS_STYLE +
                "</head><body ${landscapeContent}>" + parsedMarkdown + "\n" +
                "</body></html>"

        def os = new ByteArrayOutputStream()
        PdfConverterExtension.exportToPdf(os, html, "", this.OPTIONS)
        os.toByteArray()
    }

}
