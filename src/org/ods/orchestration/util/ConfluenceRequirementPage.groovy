package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS
import java.util.regex.Pattern
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import org.jsoup.select.Elements
import org.ods.util.ILogger

@SuppressWarnings(['LineLength'])
class ConfluenceRequirementPage {

    private static final Pattern PROPERTY_DISCONTINUED_PATTERN = ~/^\s*Discontinued\s*/
    private static final Pattern PROPERTY_REQUIREMENT_TYPE_PATTERN = ~/^\s*Requirement Type\s*/
    private static final Pattern PROPERTY_IMPLEMENTATION_TYPE_PATTERN = ~/^\s*Implementation Type\s*/
    private static final Pattern PROPERTY_PROCEDURE_PATTERN = ~/^\s*Procedure\s*/
    private static final Pattern REQUIREMENT_NUMBER_PATTERN = ~/(?i)^\s*+Requirement\s*+(\d++)\s*+:/

    private ILogger logger

    private Map<String, String> metadata
    private Map<String, String> properties
    private String content

    ConfluenceRequirementPage(String htmlPageContent, String pageQuery, ILogger logger) {
        this.logger = logger

        this.metadata = [:]
        this.properties = [:]
        this.content = ""
        logger.debugClocked("confluencePageParse-" + pageQuery)
        this.parse(htmlPageContent)
        logger.debugClocked("confluencePageParse-" + pageQuery)
    }

    ConfluenceRequirementPage(ConfluenceRequirementPage page) {
        logger = page.logger
        metadata = page.metadata.clone()
        properties = page.properties.clone()
        content = page.content
    }

    @NonCPS
    Map<String, String> getMetadata() {
        return this.metadata
    }

    @NonCPS
    Map<String, String> getProperties() {
        return this.properties
    }

    @NonCPS
    String getContent() {
        return this.content
    }

    @NonCPS
    protected void parse(String htmlPageContent) {
        def doc = Jsoup.parse(htmlPageContent)
        this.parseMetadata(doc)

        Element mainContentElement = doc.select("#main-content").first()
        this.parseProperties(mainContentElement)
        this.parseContent(mainContentElement)
    }

    @NonCPS
    protected void parseMetadata(Document doc) {
        this.metadata.pageId = doc.select("meta[name=ajs-page-id]").first()?.attr("content")
        this.metadata.latestPageId =
            doc.select("meta[name=ajs-latest-page-id]").first()?.attr("content")
        def pageTitle = doc.select("meta[name=ajs-page-title]")?.first()?.attr("content")
        this.metadata.pageTitle = pageTitle
        this.metadata.latestPageTitle =
            doc.select("meta[name=ajs-latest-published-page-title]").first()?.attr("content")
        def pageVersion = doc.select("meta[name=ajs-page-version]").first()?.attr("content")
        if (!pageVersion) {
            pageVersion = doc.select("meta[name=page-version]").first()?.attr("content")
        }
        this.metadata.pageVersion = pageVersion
        def spaceKey = doc.select("meta[name=ajs-space-key]").first()?.attr("content")
        if (!spaceKey) {
            spaceKey = doc.select("meta[name=confluence-space-key]").first()?.attr("content")
        }
        this.metadata.spaceKey = spaceKey
        this.metadata.spaceName = doc.select("meta[name=ajs-space-name]").first()?.attr("content")
        this.metadata.siteTitle = doc.select("meta[name=ajs-site-title]").first()?.attr("content")

        if (!pageTitle) {
            logger.warn('Couldn\'t determine the Confluence page title.')
            return
        }

        // parse the requirement number from the page title
        def matcher = REQUIREMENT_NUMBER_PATTERN.matcher(pageTitle)
        if (matcher.find()) {
            this.metadata.requirementNumber = matcher.group(1)
        } else {
            logger.warn(
                "Couldn't determine the requirement number from the page title '${pageTitle}'. " +
                'No tests will be matched against this requirement.'
            )
        }
    }

    @NonCPS
    protected void parseProperties(Element root) {
        Element propertiesTable = root.select(".plugin-tabmeta-details table").first()
        if (propertiesTable) {
            if (propertiesTable.select("tr:nth-child(1) th p").first()?.text() ==~ PROPERTY_REQUIREMENT_TYPE_PATTERN) {
                this.properties.requirementType = propertiesTable.select("tr:nth-child(1) td span").first()?.text()
            }

            if (propertiesTable.select("tr:nth-child(2) th p").first()?.text() ==~ PROPERTY_IMPLEMENTATION_TYPE_PATTERN) {
                this.properties.implementationType = propertiesTable.select("tr:nth-child(2) td span").first()?.text()
            }

            if (propertiesTable.select("tr:nth-child(3) th p").first()?.text() ==~ PROPERTY_PROCEDURE_PATTERN) {
                this.properties.procedure = propertiesTable.select("tr:nth-child(3) td span").first()?.text()
            }
        }
    }

    @NonCPS
    protected void parseContent(Element root) {
        // FIXME: jsoup will fail if either of the selectors does not match; must be implemented fail-safe
        Elements requirementElements = root.select("> :not(script,script *,span,.plugin-tabmeta-details,.plugin-tabmeta-details *,[data-macro-name='edp-issue-related'],[data-macro-name='edp-issue-related'] *,[data-macro-name='edp-requirement'],[data-macro-name='edp-requirement'] *)")
        requirementElements.iterator().each { element ->
            // remove attributes from all elements
            def attribsIter = element.attributes.iterator()
            attribsIter.each { attribsIter.remove() }

            // replace <h*> elements with <strong>
            if (element.normalName() ==~ /h[0-6]/) {
                element.tagName("strong")
            }

            // replace span elements with their text content
            if (element.hasText()) {
                element.replaceWith(new TextNode(element.text()))
            }
        }

        this.content = requirementElements.outerHtml()
    }

    @NonCPS
    String toString() {
        def result = new StringBuilder()
        result << this.getMetadata() << "\n"
        result << this.getProperties() << "\n"
        result << this.getContent() << "\n"
        return result
    }
}
