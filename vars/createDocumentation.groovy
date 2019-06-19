def call(repos, projectMetadata) {
    repos.each { repo ->
        def docData = createDocument('data')
        def docUrl = uploadDocumentToNexus(docData , 'org.opendevstack.rm', repo.name)
        def task = queryJiraTask(projectMetadata, 'key = "PLTF-10"')
        addCommentToJira(task.key, docUrl)
    }
}