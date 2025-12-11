package org.ods.orchestration.usecase

import com.cloudbees.groovy.cps.NonCPS
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.ConfluenceRequirementPage

class RequirementUseCase {
    private final ConfluenceUseCase confluence
    private final IPipelineSteps steps
    private final ILogger logger

    RequirementUseCase(IPipelineSteps steps, ConfluenceUseCase confluence, ILogger logger) {
        this.steps = steps
        this.confluence = confluence
        this.logger = logger
    }

    @NonCPS
    Map<URI, ConfluenceRequirementPage> getRequirementsForJiraIssues(Map<String, Map> issues, String currentVersion) {
        def requirements = [:] as Map<URI, ConfluenceRequirementPage>
        def seenBefore = [] as Set
        issues?.each { key, issue ->
            def links = issue.remoteLinks as List<Map>
            links?.findAll { !seenBefore.contains(it.url) }?.each { link ->
                def url = link.url
                seenBefore << url
                if (link.relationship == 'Requirement') {
                    def requirement = confluence.getRequirementPage(url)
                    if (requirement && (!requirement.properties.discontinued || issue.fixVersion == currentVersion)) {
                        // We list discontinued requirements if they have a related issue in the current version.
                        requirement = new ConfluenceRequirementPage(requirement)
                        requirement.metadata.url = url
                        requirements[url] = requirement
                    }
                }
            }
        }
        return requirements
    }

    @NonCPS
    SortedMap<Integer, ConfluenceRequirementPage> getRequirementsByNumber(Map<URI, ConfluenceRequirementPage> requirements) {
        def latest = new TreeMap<Integer, ConfluenceRequirementPage>()
        requirements.each { uri, requirement ->
            def number = requirement.metadata.requirementNumber as Integer
            if (number) {
                def current = latest[number]
                if (!current || requirement.metadata.pageVersion > current.metadata.pageVersion) {
                    latest[number] = requirement // Select the latest referenced version of each requirement
                }
            }
        }
        return latest
    }

}
