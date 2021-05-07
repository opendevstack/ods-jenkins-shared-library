package org.ods.util

import org.yaml.snakeyaml.Yaml

/**
 * Utility class to handle recommended and custom labels and annotations for OpenShift resources.
 *
 * @See <ahref="https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/"     >     Kubernetes: Recommended Labels</a>
 * @See <ahref="https://github.com/gorkem/app-labels/blob/master/labels-annotation-for-openshift.adoc"     >     Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://docs.openshift.com/container-platform/4.7/applications/application_life_cycle_management/odc-viewing-application-composition-using-topology-view.html#odc-labels-and-annotations-used-for-topology-view_viewing-application-composition-using-topology-view"     >     Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://helm.sh/docs/chart_best_practices/labels/"     >     Helm: Labels and Annotations</a>
 *
 */
class OpenShiftResourceMetadata {
    private final script
    private final context
    private final openShift
    private final steps
    private static final labelKeys = [
        name          : 'app.kubernetes.io/name',
        instance      : 'app.kubernetes.io/instance',
        version       : 'app.kubernetes.io/version',
        component     : 'app.kubernetes.io/component',
        partOf        : 'app.kubernetes.io/part-of',
        managedBy     : 'app.kubernetes.io/managed-by',
        runtime       : 'app.openshift.io/runtime',
        runtimeVersion: 'app.openshift.io/runtime-version',
        chart         : 'helm.sh/chart',
        owner         : 'app.opendevstack.org/project-owner',
        configItem    : 'app.opendevstack.org/config-item',
        changeId      : 'app.opendevstack.org/change-id',
    ]
    private static final annotationKeys = [
        vcsUri          : 'app.openshift.io/vcs-uri',
        vcsRef          : 'app.openshift.io/vcs-ref',
        connectsTo      : 'app.openshift.io/connects-to',
        overviewAppRoute: 'console.alpha.openshift.io/overview-app-route',
    ]
    private static final labelMappings = [
        name          : 'name',
        instance      : 'instance',
        version       : 'version',
        component     : 'role',
        partOf        : 'partOf',
        managedBy     : 'managedBy',
        runtime       : 'runtime',
        runtimeVersion: 'runtimeVersion',
        chart         : 'chart',
        owner         : 'owner',
        configItem    : 'configItem',
        changeId      : 'changeId',
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
            instance : context.componentId,
            managedBy: 'tailor',
            owner    : 'project-admin',
        ]
        return metadata
    }

    def getComponentMetadata() {
        def metadata = [:]
        if (steps.fileExists('metadata.yml')) {
            def yaml = steps.load('metadata.yml')
            metadata = new Yaml().load(yaml)
        }
        return metadata
    }

    def getMetadata() {
        def metadata = getDefaultMetadata()
        metadata.putAll(getComponentMetadata())
        metadata.putAll(getForcedMetadata())
        if (metadata.name == metadata.instance) {
            metadata.remove('instance')
        }
        return metadata
    }

    def setMetadata(metadata) {
        if (metadata == null) {
            throw new NullPointerException("Metadata cannot be null")
        }
        def labels = labelKeys.collectEntries { key, value -> [(value): metadata[labelMappings[key]]] }
        openShift.labelResources(context.targetProject, 'all', labels, context.selector)
    }

    def updateMetadata() {
        def metadata = getMetadata()
        setMetadata(metadata)
    }

}
