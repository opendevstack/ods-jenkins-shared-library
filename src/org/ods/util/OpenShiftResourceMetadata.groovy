package org.ods.util
/**
 * Utility class to handle recommended and custom labels and annotations for OpenShift resources.
 *
 * @See <ahref="https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/"   >   Kubernetes: Recommended Labels</a>
 * @See <ahref="https://github.com/gorkem/app-labels/blob/master/labels-annotation-for-openshift.adoc"   >   Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://docs.openshift.com/container-platform/4.7/applications/application_life_cycle_management/odc-viewing-application-composition-using-topology-view.html#odc-labels-and-annotations-used-for-topology-view_viewing-application-composition-using-topology-view"   >   Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://helm.sh/docs/chart_best_practices/labels/"   >   Helm: Labels and Annotations</a>
 *
 */
class OpenShiftResourceMetadata {
    private final script
    private final context
    private final openShift
    private final steps
    private static final labelMapping = [
        name          : 'app.kubernetes.io/name',
        componentId   : 'app.kubernetes.io/instance',
        version       : 'app.kubernetes.io/version',
        componentRole : 'app.kubernetes.io/component',
        partOf        : 'app.kubernetes.io/part-of',
        managedBy     : 'app.kubernetes.io/managed-by',
        runtime       : 'app.openshift.io/runtime',
        runtimeVersion: 'app.openshift.io/runtime-version',
        chart         : 'helm.sh/chart',
        owner         : 'app.opendevstack.org/project-owner',
        configItem    : 'app.opendevstack.org/config-item',
        changeId      : 'app.opendevstack.org/change-id',
        gitUri        : 'app.openshift.io/vcs-uri',
        commitHash    : 'app.openshift.io/vcs-ref',
        connectsTo    : 'app.openshift.io/connects-to',
        primaryRoute  : 'console.alpha.openshift.io/overview-app-route',
    ]

    OpenShiftResourceMetadata(script, context, openShift) {
        this.script = script
        this.context = context
        this.openShift = openShift
        steps = new PipelineSteps(script)
    }

    def getDefaultMetadata() {
        def metadata = [
            name  : context.componentId,
            partOf: context.projectId,
        ]
        return metadata
    }

    def getForcedMetadata() {
        def metadata = [
            componentId: context.componentId,
            managedBy  : 'tailor',
            owner      : 'project-admin',
        ]
        return metadata
    }

    def getProjectMetadata() {
        def metadata = new Properties()
        if (steps.fileExists('metadata.groovy')) {
            def script = steps.load('metadata.groovy')
            metadata = script.getMetadata()
        } else if (steps.fileExists('metadata.properties')) {
            def file = steps.readFile('metadata.properties', 'US-ASCII')
            metadata.load(new StringReader(file))
        }
        return metadata
    }

    def getMetadata() {
        def metadata = getDefaultMetadata()
        metadata.putAll(getProjectMetadata())
        metadata.putAll(getForcedMetadata())
        if (metadata.name == context.componentId) {
            metadata.remove('instance')
        }
        return metadata
    }

    def setMetadata(metadata) {
        def labels = metadata.collectEntries { key, value -> [(labelMapping[key]): value] }
        openShift.labelResources(context.targetProject, 'all', labels, context.selector)
    }

    def setMetadata() {
        def metadata = getMetadata()
        setMetadata(metadata)
    }

}
