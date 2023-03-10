package org.ods.orchestration.usecase

import groovy.json.JsonOutput

import org.ods.orchestration.service.DocGenService
import org.ods.services.JenkinsService
import org.ods.services.NexusService
import org.ods.util.IPipelineSteps
import org.ods.orchestration.util.MROPipelineUtil
import org.ods.orchestration.util.PDFUtil
import org.ods.orchestration.util.Project

@SuppressWarnings([
    'AbstractClassWithPublicConstructor',
    'LineLength',
    'ParameterCount',
    'GStringAsMapKey',
    'DuplicateMapLiteral'])
abstract class DocGenUseCase {

    static final String RESURRECTED = "resurrected"
    protected Project project
    protected IPipelineSteps steps
    protected MROPipelineUtil util
    protected DocGenService docGen
    protected NexusService nexus
    protected PDFUtil pdf
    protected JenkinsService jenkins

    DocGenUseCase(Project project, IPipelineSteps steps, MROPipelineUtil util, DocGenService docGen, NexusService nexus, PDFUtil pdf, JenkinsService jenkins) {
        this.project = project
        this.steps = steps
        this.util = util
        this.docGen = docGen
        this.nexus = nexus
        this.pdf = pdf
        this.jenkins = jenkins
    }

    String createDocument(String documentType, Map repo, Map data, Map<String, byte[]> files = [:], Closure modifier = null, String templateName = null, String watermarkText = null) {
        // Create a PDF document via the DocGen service
        def document = this.docGen.createDocument(templateName ?: documentType, this.getDocumentTemplatesVersion(), data)

        // Apply PDF document modifications, if provided
        if (modifier) {
            document = modifier(document)
        }

        // Apply PDF document watermark, if provided
        if (watermarkText) {
            document = this.pdf.addWatermarkText(document, watermarkText)
        }

        def basename = this.getDocumentBasename(documentType, this.project.buildParams.version, this.steps.env.BUILD_ID, repo)
        def pdfName = "${basename}.pdf"

        // Create an archive with the document and raw data
        def artifacts = [
            "${pdfName}": document,
            "raw/${basename}.json": JsonOutput.toJson(data).getBytes(),
        ]
        artifacts << files.collectEntries { path, contents ->
            [ path, contents ]
        }

        def doCreateArtifact = shouldCreateArtifact(documentType, repo)
        def artifact = this.util.createZipArtifact(
            "${basename}.zip",
            artifacts,
            doCreateArtifact
        )

        // Concerns DTR/TIR for a single repo
        if (!doCreateArtifact) {
            this.util.createAndStashArtifact(pdfName, document)
            if (repo) {
                repo.data.documents[documentType] = pdfName
            }
        }

        // Store the archive as an artifact in Nexus
        def uri = this.nexus.storeArtifact(
            this.project.services.nexus.repository.name,
            "${this.project.key.toLowerCase()}-${this.project.buildParams.version}",
            "${basename}.zip",
            artifact,
            "application/zip"
        )

        def message = "Document ${documentType}"
        if (repo) {
            message += " for ${repo.id}"
        }
        message += " uploaded @ ${uri}"
        this.steps.echo message
        return uri.toString()
    }

    @SuppressWarnings(['JavaIoPackageAccess'])
    String createOverallDocument(String templateName, String documentType, Map metadata, Closure visitor = null, String watermarkText = null) {
        def documents = []
        def sections = []

        this.project.repositories.each { repo ->
            def documentName = repo.data.documents[documentType]

            if (documentName) {
                def path = "${this.steps.env.WORKSPACE}/reports/${repo.id}"
                jenkins.unstashFilesIntoPath(documentName, path, documentType)

                documents << new File("${path}/${documentName}").readBytes()

                sections << [
                    heading: "${documentType} for component: ${repo.id} (merged)"
                ]
            }
        }

        def data = [
            metadata: metadata,
            data: [
                sections: sections
            ],
        ]

        // Apply any data transformations, if provided
        if (visitor) {
            visitor(data.data)
        }

        // Create a cover page and merge all documents into one
        def modifier = { document ->
            documents.add(0, document)
            return this.pdf.merge(this.steps.env.WORKSPACE, documents)
        }

        def result = this.createDocument(documentType, null, data, [:], modifier, templateName, watermarkText)

        // Clean up previously stored documents
        this.project.repositories.each { repo ->
            repo.data.documents.remove(documentType)
        }

        return result
    }

    String getDocumentBasename(String documentType, String version, String build = null, Map repo = null) {
        getDocumentBasenameWithDocVersion(documentType, getDocumentVersion(version, build), repo)
    }

    String getDocumentBasenameWithDocVersion(String documentType, String docVersion, Map repo = null) {
        def result = this.project.key
        if (repo) {
            result += "-${repo.id}"
        }

        return "${documentType}-${result}-${docVersion}".toString()
    }

    String getDocumentVersion(String projectVersion, String build = null) {
        if (build) {
            "${projectVersion}-${build}"
        } else {
            "${projectVersion}-${steps.env.BUILD_ID}"
        }
    }

    @SuppressWarnings(['AbcMetric'])
    Map resurrectAndStashDocument(String documentType, Map repo, boolean stash = true) {
        if (!repo.data.openshift.deployments) {
            return [found: false]
        }
        String resurrectedBuild
        if (repo.data.openshift.resurrectedBuild) {
            resurrectedBuild = repo.data.openshift.resurrectedBuild
            this.steps.echo "Using ${documentType} from jenkins build: ${resurrectedBuild}" +
                " for repo: ${repo.id}"
        } else {
            return [found: false]
        }
        def buildVersionKey = resurrectedBuild.split('/')
        if (buildVersionKey.size() != 2) {
            return [found: false]
        }

        def oldBuildVersion = buildVersionKey[0]
        def basename = getDocumentBasename(
            documentType, oldBuildVersion, buildVersionKey[1], repo)
        def path = "${this.steps.env.WORKSPACE}/reports/${repo.id}"
        def mkdirresult = new File(path).mkdir()
        this.steps.echo "Created directory ${path} for reports, success: ${mkdirresult}"

        def fileExtensions = getFiletypeForDocumentType(documentType)
        String storageType = fileExtensions.storage ?: 'zip'
        String contentType = fileExtensions.content ?: 'pdf'
        this.steps.echo "Resolved documentType '${documentType}'" +
            " - storage/content formats: ${fileExtensions}"

        String contentFileName = "${basename}.${contentType}"
        String storedFileName = "${basename}.${storageType}"
        Map documentFromNexus =
            this.nexus.retrieveArtifact(
                this.project.services.nexus.repository.name,
                "${this.project.key.toLowerCase()}-${oldBuildVersion}",
                storedFileName, path)

        this.steps.echo "Document found: ${storedFileName} \r${documentFromNexus}"
        byte [] resurrectedDocAsBytes
        if (storageType == 'zip') {
            resurrectedDocAsBytes = this.util.extractFromZipFile(
                "${path}/${storedFileName}", contentFileName)
        } else {
            resurrectedDocAsBytes = documentFromNexus.content.getBytes()
        }

        // stash doc with new name / + build id
        if (stash) {
            this.util.createAndStashArtifact(contentFileName, resurrectedDocAsBytes)
        }

        if (!shouldCreateArtifact(documentType, repo)) {
            repo.data.documents[documentType] = contentFileName
        }

        return [
            found: true,
            'uri': documentFromNexus.uri,
            content: resurrectedDocAsBytes,
            createdByBuild: resurrectedBuild,
        ]
    }

    URI storeDocument (String documentName, byte [] documentAsBytes, String contentType) {
        return this.nexus.storeArtifact(
            this.project.services.nexus.repository.name,
            "${this.project.key.toLowerCase()}-${this.project.buildParams.version}",
            "${documentName}",
            documentAsBytes,
            contentType
        )
    }

    abstract String getDocumentTemplatesVersion()

    abstract Map getFiletypeForDocumentType (String documentType)

    abstract List<String> getSupportedDocuments()

    abstract boolean shouldCreateArtifact (String documentType, Map repo)

}
