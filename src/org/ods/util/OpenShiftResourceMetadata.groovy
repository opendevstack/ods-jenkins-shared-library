package org.ods.util
/**
 * Utility class to handle recommended and custom labels and annotations for OpenShift resources.
 *
 * @See <ahref="https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/"         >         Kubernetes: Recommended Labels</a>
 * @See <ahref="https://github.com/gorkem/app-labels/blob/master/labels-annotation-for-openshift.adoc"         >         Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://docs.openshift.com/container-platform/4.7/applications/application_life_cycle_management/odc-viewing-application-composition-using-topology-view.html#odc-labels-and-annotations-used-for-topology-view_viewing-application-composition-using-topology-view"         >         Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://helm.sh/docs/chart_best_practices/labels/"         >         Helm: Labels and Annotations</a>
 *
 */
class OpenShiftResourceMetadata {
    private final script
    private final context
    private final openShift
    private final steps
    private static final labelKeys = [
        name          : 'app.kubernetes.io/name',
        version       : 'app.kubernetes.io/version',
        instance      : 'app.kubernetes.io/instance',
        component     : 'app.kubernetes.io/component',
        partOf        : 'app.kubernetes.io/part-of',
        managedBy     : 'app.kubernetes.io/managed-by',
        runtime       : 'app.openshift.io/runtime',
        runtimeVersion: 'app.openshift.io/runtime-version',
        chart         : 'helm.sh/chart',
        owner         : 'app.opendevstack.org/project-owner',
    ]
    private static final releaseManagerLabelKeys = [
        configItem: 'app.opendevstack.org/config-item',
        changeId  : 'app.opendevstack.org/change-id',
        release   : 'app.opendevstack.org/release',
    ]
    private static final annotationKeys = [
        vcsUri          : 'app.openshift.io/vcs-uri',
        vcsRef          : 'app.openshift.io/vcs-ref',
        connectsTo      : 'app.openshift.io/connects-to',
        overviewAppRoute: 'console.alpha.openshift.io/overview-app-route',
    ]
    private static final mappings = [
        name            : 'name',
        version         : 'version',
        instance        : 'componentId',
        component       : 'role',
        partOf          : 'partOf',
        managedBy       : 'managedBy',
        runtime         : 'runtime',
        runtimeVersion  : 'runtimeVersion',
        chart           : 'chart',
        owner           : 'projectAdmin',
        configItem      : 'configItem',
        changeId        : 'changeId',
        release         : 'release',
        vcsUri          : 'bitbucketUri',
        vcsRef          : 'bitbucketRef',
        connectsTo      : 'connectsTo',
        overviewAppRoute: 'primaryRoute',
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
            componentId : context.componentId,
            managedBy   : 'tailor',
            projectAdmin: 'project-admin',
        ]
        if (context.triggeredByOrchestrationPipeline) {
            metadata += [
                configItem: steps.env.BUILD_PARAM_CONFIGITEM,
                changeId  : steps.env.BUILD_PARAM_CHANGEID,
                release   : steps.env.BUILD_PARAM_RELEASE,
            ]
        }
        return metadata
    }

    def getComponentMetadata() {
        def metadata = [:]
        if (steps.fileExists('metadata.yml')) {
            metadata = steps.readYaml(file: 'metadata.yml')
        }
        return metadata
    }

    def getMetadata() {
        def metadata = getDefaultMetadata()
        metadata.putAll(getComponentMetadata())
        metadata.putAll(getForcedMetadata())
        if (metadata.name == metadata.componentId) {
            metadata.remove('componentId')
        }
        return metadata
    }

    def setMetadata(metadata) {
        if (metadata == null) {
            throw new NullPointerException("Metadata cannot be null")
        }
        def labels = labelKeys.collectEntries { key, value -> [(value): metadata[mappings[key]]] }
        if (context.triggeredByOrchestrationPipeline) {
            labels += releaseManagerLabelKeys.collectEntries { key, value ->
                [(value): metadata[mappings[key]]]
            }
        }
        openShift.labelResources(context.targetProject, 'all', labels, context.selector)
    }

    def updateMetadata() {
        def metadata = getMetadata()
        setMetadata(metadata)
    }

}
