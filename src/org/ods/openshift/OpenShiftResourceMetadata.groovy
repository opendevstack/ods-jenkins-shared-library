package org.ods.openshift


import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

/**
 * Utility class to handle recommended and custom labels and annotations for OpenShift resources.
 *
 * @See <ahref="https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/" >
 *     Kubernetes: Recommended Labels</a>
 * @See <ahref="https://github.com/gorkem/app-labels/blob/master/labels-annotation-for-openshift.adoc" >
 *     Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <ahref="https://helm.sh/docs/chart_best_practices/labels/" > Helm: Labels and Annotations</a>
 *
 */
class OpenShiftResourceMetadata {
    // Standard roles recognized by OpenShift. Arbitrary roles are supported.
    static final ROLE_FRONTEND = 'frontend'
    static final ROLE_BACKEND = 'backend'
    static final ROLE_DATABASE = 'database'
    static final ROLE_INTEGRATION = 'integration'
    static final ROLE_CACHE = 'cache'
    static final ROLE_QUEUE = 'queue'

    // Custom roles for the standard quickstarters. Arbitrary roles are supported.
    static final ROLE_SUBSYSTEM = 'subsystem' // Data-science quickstarters are assigned this role by default

    /**
     * <code>Map</code> with the supported labels.
     * Keys are identifiers for each label and values are the label keys.
     */
    private static final labelKeys = [
        name:           'app.kubernetes.io/name',
        instance:       'app.kubernetes.io/instance',
        component:      'app.kubernetes.io/component',
        partOf:         'app.kubernetes.io/part-of',
        managedBy:      'app.kubernetes.io/managed-by',
        runtime:        'app.openshift.io/runtime',
        runtimeVersion: 'app.openshift.io/runtime-version',
        chart:          'helm.sh/chart',
        owner:          'app.opendevstack.org/project-owner',
        type:           'app.opendevstack.org/type',
        systemName:     'app.opendevstack.org/system-name',
        projectVersion: 'app.opendevstack.org/project-version',
    ]

    /**
     * <code>Map</code> with the mapping between label id's and metadata id's.
     * Keys are label id's and values, metadata id's.
     */
    private static final mappings = [
        name:           'id',
        instance:       'componentId',
        component:      'role',
        partOf:         'partOf',
        managedBy:      'managedBy',
        runtime:        'runtime',
        runtimeVersion: 'runtimeVersion',
        chart:          'chart',
        owner:          'projectAdmin',
        type:           'type',
        systemName:     'systemName',
        projectVersion: 'projectVersion',
    ]

    /**
     * <code>Set</code> of label id's for labels that will be removed if they had previously been assigned,
     * to the involved resources, but no data is given for them this time.
     * Labels not included in this set will remain with their previous value, if no new value is given for them.
     */
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

    /**
     * Initializes the data, utilities and services needed for this functionality.
     *
     * <code>context</code> usually implements either <code>org.ods.component.IContext</code>
     * or <code>org.ods.quickstarter.IContext</code>.
     * We are using a <code>Map</code> to make it independent of the concrete interface.
     * Please, use <code>context.properties</code> to get the <code>Map</code> for the context instance.
     *
     * For the <code>config</code> parameter, sometimes an <code>Options</code> instance is available.
     * Please, use <code>options.properties</code> to get the needed <code>Map</code>.
     * <code>config</code> should contain, at least, the following properties:
     *
     * <code>selector</code>: a label selector to filter the resources to apply the labels.
     * Usually, <code>app=${project}-${component}</code>.
     * If this is not given, it defaults to <code>context.selector</code>, if available.
     *
     * <code>environment</code>, used to build the target project id, if <code>context.targetProject</code>
     * is not available.
     *
     * <code>chartDir</code>: required to support helm-managed components. If not available, the component
     * will be assumed to be a regular tailor-managed one.
     *
     * @param steps provides access to Jenkins pipeline steps.
     * Test implementation is available to be used when not running in Jenkins.
     * @param context the pipeline context.
     * @param config additional configuration provided.
     * @param logger an <code>ILogger</code> instance.
     * @param openShift an optional instance of <code>OpenShiftService</code>. A new one will be created, if not given.
     */
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
     *
     * @throws IllegalArgumentException if the target OpenShift project cannot be guessed from the available data.
     * @throws RuntimeException if there is an error setting the labels and annotations in OpenShift.
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
     * @throws IllegalArgumentException if the target OpenShift project cannot be guessed from the available data.
     * @throws RuntimeException if there is an error setting the labels and annotations in OpenShift.
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
     * @return a <code>Map</code> with the user-or-quickstarter-provided metadata. Empty map, if none found.
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
            chart:       null as String,
        ]

        // Find out whether this component is managed by helm
        def chart = getChartNameAndVersion()
        if (chart) {
            metadata.managedBy = 'helm'
            metadata.chart = chart
        }

        // When triggered by the release manager, it provides some of the metadata.
        if (context.triggeredByOrchestrationPipeline) {
            metadata.putAll([
                systemName:     steps.env?.BUILD_PARAM_CONFIGITEM as String,
                projectVersion: steps.env?.BUILD_PARAM_CHANGEID as String,
            ])
            if (metadata.systemName?.startsWith('mailto:')) {
                metadata.systemName = ''
            }
        }

        return metadata
    }

    /**
     * Try to guess the target OpenShift project where we are going to set the labels and annotations.
     * It first tries to find it in the context and, if not successful, it tries to build it from
     * the project id and the environment.
     * It is a runtime error that the needed data is not available.
     *
     * @return the target OpenShift project, with the format <code>${projectId}-${environment}</code>.
     * @throws IllegalArgumentException if the target project cannot be guessed from the available data.
     */
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

    /**
     * Reads the metadata from an existing component.
     *
     * @return a <code>Map</code> with the metadata set up in the component.
     */
    private getMetadataFromComponent() {
        return readMetadata('metadata.yml')
    }

    /**
     * Reads the default metadata for components provisioned from the current quickstarter
     * from the quickstarter repository.
     *
     * @return a <code>Map</code> with the default metadata for components provisioned with the current quickstarter.
     */
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
            if (steps.fileExists(chartPath)) {
                def chart = steps.readYaml(file: chartPath)
                return "${chart.name}-${chart.version.replace('+' as char, '_' as char)}"
            }
        }
        return null
    }

    /**
     * Reads metadata from a YAML file given in <code>metadataPath</code>.
     *
     * @param metadataPath a YAML file to read metadata from.
     * @return a <code>Map</code> with the metadata contained in the given <code>metadataPath</code>.
     */
    private readMetadata(metadataPath) {
        if (steps.fileExists(metadataPath)) {
            return steps.readYaml(file: metadataPath)
        }
        return null
    }

}
