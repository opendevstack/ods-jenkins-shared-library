package org.ods.util

import groovy.json.JsonSlurper

import java.nio.file.Paths

import org.apache.http.client.utils.URIBuilder
import org.yaml.snakeyaml.Yaml

class Project {

    class TestType {
        static final String ACCEPTANCE = "AcceptanceTests"
        static final String INSTALLATION = "InstallationTest"
        static final String INTEGRATION = "IntegrationTest"
        static final String UNIT = "UnitTest"
    }

    class GampTopic {
        static final String AVAILABILITY_REQUIREMENT = "Availability Requirement"
        static final String CONSTRAINT = "Constraint"
        static final String FUNCTIONAL_REQUIREMENT = "Functional Requirement"
        static final String INTERFACE_REQUIREMENT = "Interface Requirement"
    }

    protected static final String METADATA_FILE_NAME = "metadata.yml"

    protected IPipelineSteps steps
    protected GitUtil git

    protected Map data = [:]

    Project(IPipelineSteps steps, GitUtil git) {
        this.steps = steps
        this.git = git
    }

    Project load() {
        this.data.build = [:]
        this.data.build.hasFailingTests = false

        this.data.buildParams = loadBuildParams(steps)
        this.data.git = [ commit: git.getCommit(), url: git.getURL() ]
        this.data.metadata = this.loadMetadata(METADATA_FILE_NAME)
        this.data.jira = this.loadJiraData(this.data.metadata.id)

        return this
    }

    List<Map> getAutomatedTests(String componentName = null, List<String> testTypes = []) {
        return this.data.jira.tests.findAll { testIssue ->
            def result = testIssue.status.toLowerCase() == "ready to test"

            if (result && componentName) {
                result = testIssue.components.collect{ it.toLowerCase() }.contains(componentName.toLowerCase()) 
            }

            if (result && testTypes) {
                result = testTypes.collect{ it.toLowerCase() }.contains(testIssue.testType.toLowerCase()) 
            }

            return result
        }
    }

    List<Map> getAutomatedTestsTypeAcceptance(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.ACCEPTANCE])
    }

    List<Map> getAutomatedTestsTypeInstallation(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.INSTALLATION])
    }

    List<Map> getAutomatedTestsTypeIntegration(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.INTEGRATION])
    }

    List<Map> getAutomatedTestsTypeUnit(String componentName = null) {
        return this.getAutomatedTests(componentName, [TestType.UNIT])
    }

    Map getBuildParams() {
        return this.data.buildParams
    }

    static List<String> getBuildEnvironment(IPipelineSteps steps, boolean debug = false) {
        def params = loadBuildParams(steps)

        return [
            "DEBUG=${debug}",
            "MULTI_REPO_BUILD=true",
            "MULTI_REPO_ENV=${params.targetEnvironment}",
            "MULTI_REPO_ENV_TOKEN=${params.targetEnvironmentToken}",
            "RELEASE_PARAM_CHANGE_ID=${params.changeId}",
            "RELEASE_PARAM_CHANGE_DESC=${params.changeDescription}",
            "RELEASE_PARAM_CONFIG_ITEM=${params.configItem}",
            "RELEASE_PARAM_VERSION=${params.version}",
            "SOURCE_CLONE_ENV=${params.sourceEnvironmentToClone}",
            "SOURCE_CLONE_ENV_TOKEN=${params.sourceEnvironmentToCloneToken}"
        ]
    }

    List getCapabilities() {
        return this.data.metadata.capabilities
    }

    List<Map> getComponents() {
        return this.data.jira.components
    }

    String getDescription() {
        return this.data.metadata.description
    }

    Map getGitData() {
        return this.data.git
    }

    protected URI getGitURLFromPath(String path, String remote = "origin") {
        if (!path?.trim()) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'path' is undefined.")
        }

        if (!path.startsWith(this.steps.env.WORKSPACE)) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'path' must be inside the Jenkins workspace: ${path}")
        }

        if (!remote?.trim()) {
            throw new IllegalArgumentException("Error: unable to get Git URL. 'remote' is undefined.")
        }

        def result = null

        this.steps.dir(path) {
            result = this.steps.sh(
                label : "Get Git URL for repository at path '${path}' and origin '${remote}'",
                script: "git config --get remote.${remote}.url",
                returnStdout: true
            ).trim()
        }

        return new URIBuilder(result).build()
    }

    String getId() {
        return this.data.jira.id 
    }

    String getKey() {
        return this.data.metadata.key
    }

    String getName() {
        return this.data.metadata.name
    }

    List<Map> getRepositories() {
        return this.data.metadata.repositories
    }

    private File getResource(String path) {
        path = path.startsWith('/') ? path : '/' + path
        new File(getClass().getResource(path).toURI())
    }

    Map getServices() {
        return this.data.metadata.services
    }

    List<Map> getSystemRequirements(String componentName = null, List<String> gampTopics = []) {
        return this.data.jira.requirements.findAll { req ->
            def result = true

            if (result && componentName) {
                result = req.components.collect{ it.toLowerCase() }.contains(componentName.toLowerCase())
            }

            if (result && gampTopics) {
                result = gampTopics.collect{ it.toLowerCase() }.contains(req.gampTopic.toLowerCase())
            }

            return result
        }
    }

    List<Map> getSystemRequirementsTypeAvailability(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.AVAILABILITY_REQUIREMENT])
    }

    List<Map> getSystemRequirementsTypeConstraints(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.CONSTRAINT])
    }

    List<Map> getSystemRequirementsTypeFunctional(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.FUNCTIONAL_REQUIREMENT])
    }

    List<Map> getSystemRequirementsTypeInterfaces(String componentName = null) {
        return this.getSystemRequirements(componentName, [GampTopic.INTERFACE_REQUIREMENT])
    }

    List<Map> getTechnicalSpecifications(String componentName = null) {
        return this.data.jira.techSpecs.findAll { techSpec ->
            def result = true

            if (result && componentName) {
                result = techSpec.components.collect{ it.toLowerCase() }.contains(componentName.toLowerCase())
            }

            return result
        }
    }

    boolean hasFailingTests() {
        return this.data.build.hasFailingTests
    }

    static Map loadBuildParams(IPipelineSteps steps) {
        def version = steps.env.version?.trim() ?: "WIP"
        def targetEnvironment = steps.env.environment?.trim() ?: "dev"
        def targetEnvironmentToken = targetEnvironment[0].toUpperCase()
        def sourceEnvironmentToClone = steps.env.sourceEnvironmentToClone?.trim() ?: targetEnvironment
        def sourceEnvironmentToCloneToken = sourceEnvironmentToClone[0].toUpperCase()

        def changeId = steps.env.changeId?.trim() ?: "${version}-${targetEnvironment}"
        def configItem = steps.env.configItem?.trim() ?: "UNDEFINED"
        def changeDescription = steps.env.changeDescription?.trim() ?: "UNDEFINED"

        return [
            changeDescription: changeDescription,
            changeId: changeId,
            configItem: configItem,
            sourceEnvironmentToClone: sourceEnvironmentToClone,
            sourceEnvironmentToCloneToken: sourceEnvironmentToCloneToken,
            targetEnvironment: targetEnvironment,
            targetEnvironmentToken: targetEnvironmentToken,
            version: version
        ]
    }

    protected Map loadJiraData(String projectKey) {
        // FIXME: we temporarily use fake data
        def file = this.getResource("project-jira-data.json")
        return new JsonSlurper().parse(file)
    }

    protected Map loadMetadata(String filename = METADATA_FILE_NAME) {
        if (filename == null) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. 'filename' is undefined.")
        }

        def file = Paths.get(this.steps.env.WORKSPACE, filename).toFile()
        if (!file.exists()) {
            throw new RuntimeException("Error: unable to load project meta data. File '${this.steps.env.WORKSPACE}/${filename}' does not exist.")
        }

        def result = new Yaml().load(file.text)

        // Check for existence of required attribute 'id'
        if (!result?.id?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'id' is undefined.")
        }

        // Check for existence of required attribute 'name'
        if (!result?.name?.trim()) {
            throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'name' is undefined.")
        }

        if (result.description == null) {
            result.description = ""
        }

        if (result.repositories == null) {
            result.repositories = []
        }

        result.repositories.eachWithIndex { repo, index ->
            // Check for existence of required attribute 'repositories[i].id'
            if (!repo.id?.trim()) {
                throw new IllegalArgumentException("Error: unable to parse project meta data. Required attribute 'repositories[${index}].id' is undefined.")
            }

            repo.data = [:]
            repo.data.documents = [:]

            // Set repo type, if not provided
            if (!repo.type?.trim()) {
                repo.type = MROPipelineUtil.PipelineConfig.REPO_TYPE_ODS_CODE
            }

            // Resolve repo URL, if not provided
            if (!repo.url?.trim()) {
                this.steps.echo("Could not determine Git URL for repo '${repo.id}' from project meta data. Attempting to resolve automatically...")

                def gitURL = this.getGitURLFromPath(this.steps.env.WORKSPACE, "origin")
                if (repo.name?.trim()) {
                    repo.url = gitURL.resolve("${repo.name}.git").toString()
                    repo.remove("name")
                } else {
                    repo.url = gitURL.resolve("${result.id.toLowerCase()}-${repo.id}.git").toString()
                }

                this.steps.echo("Resolved Git URL for repo '${repo.id}' to '${repo.url}'")
            }

            // Resolve repo branch, if not provided
            if (!repo.branch?.trim()) {
                this.steps.echo("Could not determine Git branch for repo '${repo.id}' from project meta data. Assuming 'master'.")
                repo.branch = "master"
            }
        }

        if (result.capabilities == null) {
            result.capabilities = []
        }

        return result
    }

    void setHasFailingTests(boolean status) {
        this.data.build.hasFailingTests = status
    }
}
