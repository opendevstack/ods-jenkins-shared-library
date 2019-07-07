// Create a validation document from template type, version, and data, and return the document
def call(String type, String version, Map data) {
    if (!env.DOCGEN_SCHEME) {
        error "Error: unable to connect to DocGen: env.DOCGEN_SCHEME is undefined"
    }

    if (!env.DOCGEN_HOST) {
        error "Error: unable to connect to DocGen: env.DOCGEN_HOST is undefined"
    }

    if (!env.DOCGEN_PORT) {
        error "Error: unable to connect to DocGen: env.DOCGEN_PORT is undefined"
    }

    def result = new org.ods.service.DocGenService(
        [
            scheme: env.DOCGEN_SCHEME,
            host: env.DOCGEN_HOST,
            port: Integer.parseInt(env.DOCGEN_PORT)
        ]
    ).createDocument(type, version, data)

    return result.data
}

return this
