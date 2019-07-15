import org.ods.service.DocGenService

// Create a validation document from template type, version, and data, and return the document
def call(String type, String version, Map data) {
    def result = new DocGenService(env.DOCGEN_URL)
        .createDocument(type, version, data)

    return result.data
}

return this
