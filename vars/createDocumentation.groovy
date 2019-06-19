def call(stage, repos, projectMetadata) {
    repos.each { repo ->
        def docData = createDocument('data')
        def docUrl = uploadDocumentToNexus(docData , 'org.opendevstack.rm', repo.name)
        def task = queryJiraTask(projectMetadata, "project  = \"${projectMetadata.services.jira.project.key}\" AND labels = VP")
        def comment = "New [InstallationReport|${docUrl}] generated: ${docUrl}"
        addCommentToJira(task.key, comment)
    }
}