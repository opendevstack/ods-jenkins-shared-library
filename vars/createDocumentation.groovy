def call(projectMetadata) {
    def docData = createDocument('data')
    def docUrl = uploadDocumentToNexus(docData)
    def task = queryJiraTask(projectMetadata, 'key = "PLTF-10"')
    addCommentToJira(task.key, docUrl)
}