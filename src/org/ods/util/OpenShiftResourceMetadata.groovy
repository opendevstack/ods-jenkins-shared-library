package org.ods.util


import org.ods.services.OpenShiftService

/**
 * Utility class to handle recommended and custom labels and annotations for OpenShift resources.
 *
 * @See <a href="https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/">
 *     Kubernetes: Recommended Labels</a>
 * @See <a href="https://github.com/gorkem/app-labels/blob/master/labels-annotation-for-openshift.adoc">
 *     Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <a href="https://helm.sh/docs/chart_best_practices/labels/">Helm: Labels and Annotations</a>
 *
 */
class OpenShiftResourceMetadata {
    // Standard roles recognized by OpenShift
    static final ROLE_FRONTEND = 'frontend'
    static final ROLE_BACKEND = 'backend'
    static final ROLE_DATABASE = 'database'
    static final ROLE_INTEGRATION = 'integration'
    static final ROLE_CACHE = 'cache'
    static final ROLE_QUEUE = 'queue'

    // Custom roles for the standard quickstarters
    static final ROLE_SUBSYSTEM = 'subsystem' // Data-science quickstarters are assigned this role by default

    private static final labelKeys = [
        name:                 'app.kubernetes.io/name',
        version:              'app.kubernetes.io/version',
        instance:             'app.kubernetes.io/instance',
        component:            'app.kubernetes.io/component',
        partOf:               'app.kubernetes.io/part-of',
        managedBy:            'app.kubernetes.io/managed-by',
        runtime:              'app.openshift.io/runtime',
        runtimeVersion:       'app.openshift.io/runtime-version',
        chart:                'helm.sh/chart',
        owner:                'app.opendevstack.org/project-owner',
        odsVersion:           'app.opendevstack.org/ods-version',
        project:              'app.opendevstack.org/project',
        type:                 'app.opendevstack.org/type',
        systemName:           'app.opendevstack.org/system-name',
        projectVersion:       'app.opendevstack.org/project-version',
    ]

    private static final mappings = [
        name:                 'id',
        version:              'version',
        instance:             'componentId',
        component:            'role',
        partOf:               'partOf',
        managedBy:            'managedBy',
        runtime:              'runtime',
        runtimeVersion:       'runtimeVersion',
        chart:                'chart',
        owner:                'projectAdmin',
        odsVersion:           'odsVersion',
        project:              'projectId',
        type:                 'type',
        systemName:           'systemName',
        projectVersion:       'projectVersion',
    ]

    // These are the only metadata entries that can be removed after being set.
    private static final removableKeys = [
        'instance',
        'partOf',
        'runtime',
        'runtimeVersion',
        'chart',
    ] as Set

    private final context
    private final config
    private final logger
    private final steps
    private final openShift

    OpenShiftResourceMetadata(
        IPipelineSteps steps,
        Map context,
        Map config,
        ILogger logger,
        OpenShiftService openShift = null
    ) {
        this.steps = steps
        this.context = context
        this.config = config
        this.logger = logger
        this.openShift = openShift ?: new OpenShiftService(steps, logger)
    }

    /**
     * Retrieves metadata for the component and sets the suitable labels and annotations
     * to the component resources.
     */
    void updateMetadata() {
        def metadata = getMetadata()
        setMetadata(metadata)
    }

    /**
     * Retrieves metadata for the component.
     *
     * @return a <code>Map</code> with the metadata for the component.
     */
    private getMetadata() {
        def metadata = getDefaultMetadata()
        metadata.putAll(getComponentMetadata())
        metadata.putAll(getMandatoryMetadata())
        if (metadata.id == metadata.componentId) {
            metadata.remove('componentId')
        }
        return metadata
    }

    /**
     * Sets the suitable labels and annotations to the component resource from the given metadata.
     * It will only use entries whose keys are one of the values in the <code>mappings Map</code>.
     *
     * @param metadata a <code>Map</code> with the metadata to use to set the labels.
     */
    private setMetadata(metadata) {
        // TODO Make sure the user cannot override the labels set by the release manager in a previous deployment.
        def labels = labelKeys
            .findAll { key, value -> removableKeys.contains(key) || metadata[mappings[key]] != null }
            .collectEntries { key, value -> [(value): metadata[mappings[key]]] }
        openShift.labelResources(getTargetProject(), 'all', labels, config.selector)
    }

    /**
     * Provides default metadata values, which may be overridden by the component configuration.
     *
     * @return a <code>Map</code> with the default metadata for the component.
     */
    private getDefaultMetadata() {
        def metadata = [
            id: context.componentId,
        ]
        def role = guessRoleFromQuickStarterName()
        if (role) {
            metadata.role = role
        }
        return metadata
    }

    /**
     * Gathers metadata provided by the user in the component configuration.
     * This metadata is meant to override the default metadata,
     * but to be overridden by the mandatory metadata.
     *
     * Quickstarters also provide some default metadata for the components generated from them.
     * This quickstarter-provided metadata will be retrieved, also, when first provisioning the component.
     *
     * @return a <code>Map</code> with the user-or-quickstarter-provided metadata.
     */
    private getComponentMetadata() {
        def metadata = getMetadataFromComponent()
        if (metadata == null) {
            metadata = getMetadataFromQuickStarter()
        }
        return metadata ?: [:]
    }

    /**
     * Provides mandatory metadata that should not be overridden by the user.
     * This metadata is expected, instead, to override user metadata.
     *
     * @return a <code>Map</code> with mandatory metadata for the component.
     */
    private getMandatoryMetadata() {
        def metadata = [
            componentId: context.componentId,
            managedBy:   'tailor',
            //projectAdmin: 'project-admin',
            project:     context.projectId,
        ]

        // Find out whether this component is managed by helm
        def chart = getChartNameAndVersion()
        if (chart) {
            metadata.managedBy = 'helm'
            metadata.chart = chart
        }

        // When triggered by the release manager, it provides some of the metadata.
        if (context.triggeredByOrchestrationPipeline) {
            metadata += [
                systemName:           steps.env.BUILD_PARAM_CONFIGITEM,
                projectVersion:       steps.env.BUILD_PARAM_CHANGEID,
            ]
            if (steps.env.BUILD_PARAM_CONFIGITEM.startsWith('mailto:')) {
                metadata.systemName = ''
            }
        }

        return metadata
    }

    private getTargetProject() {
        def project = context.targetProject
        if (!project) {
            if (!config.environment) {
                throw new IllegalArgumentException(
                    "Couldn't guess target project for ${context.projectId}. Unknown environment."
                )
            }
            project = "${context.projectId}-${config.environment}"
        }
        return project
    }

    /**
     * Tries to guess the role of the component from the quickstarter name.
     * This can be done only when provisioning the component for the first time, not when redeploying.
     * However, when redeploying, the corresponding labels are expected to be already set,
     * and the role will only change if explicitly set up by the user.
     *
     * It can only guess the role for quickstarters following the naming convention of using prefixes related
     * to the nature of the component: 'be-' for backend, 'fe-' for frontend, 'de-' for data science.
     *
     * @return the role of the component or <code>null</code>, if it could not be guessed.
     */
    private guessRoleFromQuickStarterName() {
        def quickStarterName = context.sourceDir
        if (!quickStarterName) {
            return null
        }
        if (quickStarterName.startsWith('fe-')) {
            return ROLE_FRONTEND
        }
        if (quickStarterName.startsWith('ds-')) {
            return ROLE_SUBSYSTEM
        }
        if (quickStarterName.startsWith('be-') && !quickStarterName.startsWith('be-fe-')) {
            return ROLE_BACKEND
        }
        return null
    }

    private getMetadataFromComponent() {
        return readMetadata('metadata.yml')
    }

    private getMetadataFromQuickStarter() {
        return readMetadata("${context.componentId}/metadata.yml")
    }

    /**
     * Searches for a helm main chart file in the component.
     * If found, it determines the chart name and version string,
     * with format name-version.
     * It replaces any '+' symbol in the version with a '_' symbol.
     *
     * @return the chart name-version string, or null if no chart file can be found.
     */
    private getChartNameAndVersion() {
        if (config.chartDir) {
            def chartPath = "${config.chartDir}/Chart.yaml"
            if(steps.fileExists(chartPath)) {
                def chart = steps.readYaml(file: chartPath)
                return "${chart.name}-${chart.version.replace('+' as char, '_' as char)}"
            }
        }
        return null
    }

    private readMetadata(metadataPath) {
        if (steps.fileExists(metadataPath)) {
            return steps.readYaml(file: metadataPath)
        }
        return null
    }

}
