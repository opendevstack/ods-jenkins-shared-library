package org.ods.util
/**
 * Utility class to handle recommended and custom labels and annotations for OpenShift resources.
 *
 * @See <ahref="https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/"                             >                             Kubernetes: Recommended Labels</a>
 * @See <ahref="https://github.com/gorkem/app-labels/blob/master/labels-annotation-for-openshift.adoc"                             >                             Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://docs.openshift.com/container-platform/4.7/applications/application_life_cycle_management/odc-viewing-application-composition-using-topology-view.html#odc-labels-and-annotations-used-for-topology-view_viewing-application-composition-using-topology-view"                             >                             Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://helm.sh/docs/chart_best_practices/labels/"                             >                             Helm: Labels and Annotations</a>
 *
 */
class OpenShiftResourceMetadata {
    private final script
    private final context
    private final options
    private final openShift
    private final steps
    private static final labelKeys = [
        name              : 'app.kubernetes.io/name',
        version           : 'app.kubernetes.io/version',
        instance          : 'app.kubernetes.io/instance',
        component         : 'app.kubernetes.io/component',
        partOf            : 'app.kubernetes.io/part-of',
        managedBy         : 'app.kubernetes.io/managed-by',
        runtime           : 'app.openshift.io/runtime',
        runtimeVersion    : 'app.openshift.io/runtime-version',
        chart             : 'helm.sh/chart',
        owner             : 'app.opendevstack.org/project-owner',
        baseRuntime       : 'app.opendevstack.org/base-runtime',
        baseRuntimeVersion: 'app.opendevstack.org/base-runtime-version',
        odsVersion        : 'app.opendevstack.org/ods-version',
        project           : 'app.opendevstack.org/project',
        type              : 'app.opendevstack.org/type',
        lang              : 'app.opendevstack.org/lang',
        langVersion       : 'app.opendevstack.org/lang-version',
    ]
    private static final releaseManagerLabelKeys = [
        systemName          : 'app.opendevstack.org/system-name',
        projectVersion      : 'app.opendevstack.org/project-version',
        projectVersionStatus: 'app.opendevstack.org/project-version-status',
    ]
    private static final annotationKeys = [
        vcsUri          : 'app.openshift.io/vcs-uri',
        vcsRef          : 'app.openshift.io/vcs-ref',
        connectsTo      : 'app.openshift.io/connects-to',
        overviewAppRoute: 'console.alpha.openshift.io/overview-app-route',
        contactWith     : 'app.opendevstack.org/contact-with'
    ]
    private static final mappings = [
        name                : 'appName',
        version             : 'appVersion',
        instance            : 'componentId',
        component           : 'role',
        partOf              : 'partOf',
        managedBy           : 'managedBy',
        runtime             : 'runtime',
        runtimeVersion      : 'runtimeVersion',
        chart               : 'chart',
        owner               : 'projectAdmin',
        baseRuntime         : 'name',
        baseRuntimeVersion  : 'baseRuntimeVersion',
        odsVersion          : 'version',
        project             : 'projectId',
        type                : 'type',
        lang                : 'lang',
        langVersion         : 'langVersion',
        systemName          : 'systemName',
        projectVersion      : 'projectVersion',
        projectVersionStatus: 'projectVersionStatus',
        vcsUri              : 'bitbucketUri',
        vcsRef              : 'bitbucketRef',
        connectsTo          : 'connectsTo',
        overviewAppRoute    : 'primaryRoute',
        contactWith         : 'contactWith',
    ]

    OpenShiftResourceMetadata(script, context, options, openShift) {
        this.script = script
        this.context = context
        this.options = options
        this.openShift = openShift
        steps = new PipelineSteps(script)
    }

    def getDefaultMetadata() {
        def metadata = [
            appName: context.componentId,
            partOf : context.projectId,
        ]
        if (context.componentId.startsWith('fe-')) {
            metadata.role = 'frontend'
        } else if (context.componentId.startsWith('ds-')) {
            metadata.role = 'subsystem'
        } else if (context.componentId.startsWith('be-') && !context.componentId.startsWith('be-fe-')) {
            metadata.role = 'backend'
        }
        return metadata
    }

    def getForcedMetadata() {
        def metadata = [
            componentId : context.componentId,
            managedBy   : 'tailor',
            //projectAdmin: 'project-admin',
            project     : context.projectId,
        ]
        if (steps.fileExists("${options.chartDir}/Chart.yaml")) {
            metadata.managedBy = 'helm'
            def chart = steps.readYaml(file: "${options.chartDir}/Chart.yaml")
            metadata.chart = "${chart.name}-${chart.version.replace('+' as char, '_' as char)}"
        }
        if (context.triggeredByOrchestrationPipeline) {
            metadata += [
                systemName          : steps.env.BUILD_PARAM_CONFIGITEM,
                projectVersion      : steps.env.BUILD_PARAM_CHANGEID,
                projectVersionStatus: steps.env.BUILD_PARAM_VERSION == 'WIP' ? 'WIP' : 'RELEASE',
            ]
            if (steps.env.BUILD_PARAM_CONFIGITEM.startsWith('mailto:')) {
                metadata.contactWith = steps.env.BUILD_PARAM_CONFIGITEM
                metadata.systemName = ''
            }
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
            throw new NullPointerException('Metadata cannot be null')
        }
        def labels = labelKeys.collectEntries { key, value -> [(value): metadata[mappings[key]]] }
        if (context.triggeredByOrchestrationPipeline) {
            // TODO The idea was to let the user specify these labels, while the release manager would override them.
            // The problem is that we don't want a non-release-manager deployment to override the values previously
            // set by the RM. This is tricky to solve, so for the moment only the RM sets these labels.
            labels += releaseManagerLabelKeys
            // .findAll { metadata[mappings[it.key]] != null }
                .collectEntries { key, value -> [(value): metadata[mappings[key]]] }
        }
        openShift.labelResources(context.targetProject, 'all', labels, options.selector)
    }

    def updateMetadata() {
        def metadata = getMetadata()
        setMetadata(metadata)
    }

}
