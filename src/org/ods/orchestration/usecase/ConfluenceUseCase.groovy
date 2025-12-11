package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.ods.orchestration.util.ConfluenceRequirementPage
import org.ods.orchestration.util.Project
import org.ods.services.ConfluenceService
import org.ods.util.ILogger

import java.util.regex.Pattern

class ConfluenceUseCase {
    private static final Pattern IMG_PATTERN = ~/(<img\s(?s:.*?)src=")(?!data:)([^"]*+)("(?s:.*?)>)/

    private Project project
    private ConfluenceService confluence
    private ILogger logger

    ConfluenceUseCase(Project project, ConfluenceService confluence, ILogger logger) {
        this.project = project
        this.confluence = confluence
        this.logger = logger
    }

    @NonCPS
    ConfluenceRequirementPage getRequirementPage(URI pageURI) {
        def htmlPageContent = this.confluence.getPage(pageURI)
        return new ConfluenceRequirementPage(htmlPageContent, this.logger)
    }

    @NonCPS
    String embedImages(CharSequence html, URI baseURL = null) {
        return html.replaceAll(IMG_PATTERN) { List<String> groups ->
            def src = groups[2]
            try {
                def url = new URI(src)
                if (baseURL && !url.isAbsolute()) {
                    logger.debug "Found relative URL: ${url}"
                    url = baseURL.resolve(url)
                    logger.debug "Resolved URL: ${url}"
                }
                url.toURL()
                def path = url.path
                def extensionIndex = path.lastIndexOf('.')
                if (extensionIndex < 0 || ++extensionIndex == path.length()) {
                    logger.warn "Couldn't retrieve image for src=\"${src}\": Unable to determine the Content Type."
                    return ''
                }
                def extension = path.substring(extensionIndex).toLowerCase(Locale.ENGLISH)
                def contentType = "image/${extension}"
                def img = confluence.getBase64(url)
                return "${groups[1]}data:${contentType};base64,${img}${groups[3]}"
            } catch (IllegalArgumentException | URISyntaxException | MalformedURLException e) {
                logger.warn("Couldn't retrieve image from src=\"${src}\": ${e.toString()}")
                return ''
            }
        }
    }

}
