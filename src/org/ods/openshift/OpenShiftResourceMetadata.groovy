package org.ods.openshift

import groovy.transform.TypeChecked
import org.ods.services.OpenShiftService
import org.ods.util.ILogger
import org.ods.util.IPipelineSteps

/**
 * Utility class to handle recommended and custom labels and annotations for OpenShift resources.
 *
 * @See <a href="https://kubernetes.io/docs/concepts/overview/working-with-objects/common-labels/" >
 *     Kubernetes: Recommended Labels</a>
 * @See <a href="https://github.com/gorkem/app-labels/blob/master/labels-annotation-for-openshift.adoc" >
 *     Guidelines for Labels and Annotations for OpenShift applications</a>
 * @See <a href="https://helm.sh/docs/chart_best_practices/labels/" > Helm: Labels and Annotations</a>
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

    private static final LABEL_VALUE_PATTERN = ~/[^\w.\-]/

    /**
     * <code>Map</code> with the supported labels.
     * Keys are identifiers for each label and values are the label keys.
     */
    private static final labelKeys = [
        name:           'app.kubernetes.io/name',
        version:        'app.kubernetes.io/version',
        instance:       'app.kubernetes.io/instance',
        component:      'app.kubernetes.io/component',
        partOf:         'app.kubernetes.io/part-of',
        managedBy:      'app.kubernetes.io/managed-by',
        runtime:        'app.openshift.io/runtime',
        runtimeVersion: 'app.openshift.io/runtime-version',
        chart:          'helm.sh/chart',
        type:           'app.opendevstack.org/type',
        systemName:     'app.opendevstack.org/system-name',
        project:        'app.opendevstack.org/project',
        projectVersion: 'app.opendevstack.org/project-version',
        workInProgress: 'app.opendevstack.org/work-in-progress',
    ]

    /**
     * <code>Map</code> with the mapping between label id's and metadata id's.
     * Keys are label id's and values, metadata id's.
     */
    private static final labelToMetadataMapping = [
        name:           'name',
        version:        'version',
        instance:       'componentId',
        component:      'role',
        partOf:         'partOf',
        managedBy:      'managedBy',
        runtime:        'runtime',
        runtimeVersion: 'runtimeVersion',
        chart:          'chartNameAndVersion',
        type:           'type',
        systemName:     'systemName',
        project:        'projectId',
        projectVersion: 'projectVersion',
        workInProgress: 'workInProgress',
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

    /**
     * The following metadata entries cannot have their values modified.
     * If the values are not valid for a label, the labelling process will fail with an exception.
     * Entries not in this list can be sanitized to match the allowed character set for labels, as follows.
     * Label values can have alphanumerical values and the characters '.', '-' and '_',
     * and must start and end with a letter or a digit.
     * Any heading and trailing non-alphanumeric characters will be trimmed.
     * If they still contain illegal characters, they will be replaced with underscores.
     * Moreover, label values cannot be longer than 63 characters. Any characters after the 63rd will also be trimmed.
     */
    private static final strictEntries = [
        'projectVersion',
        'systemName',
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
    @TypeChecked
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
     * It also adds the labels to the templates in the Deployments and DeploymentConfigs.
     * The optional parameter <code>deployments</code> allows to specify the concrete
     * Deployments or DeploymentConfigs to update. None, if empty. If null (default),
     * all the Deployments and DeploymentConfigs found in the target project using <code>config.selector</code>.
     * The optional parameter <code>pauseRollouts</code> allows to pause or resume rollouts at the same time we update
     * the template labels. If <code>null</code> (default), it will leave the current value untouched.
     * Otherwise, it will pause (<code>true</code>) or resume (<code>false</code>) rollouts.
     *
     * @param pauseRollouts whether to pause rollouts to avoid triggering deployments when updating the labels
     * in <code>dc</code> and <code>deploy</code> templates. Default: do not change.
     * @param deployments a map of resource kind to a list of resource names of all the deployments for which to
     * update the labels in their template. Autodetected, if <code>null</code>.
     * @throws IllegalArgumentException if the target OpenShift project cannot be guessed from the available data,
     * or if some invalid metadata was found.
     * @throws RuntimeException if there is an error setting the labels and annotations in OpenShift.
     */
    void updateMetadata(Boolean pauseRollouts = null, Map<String, List<String>> deployments = null) {
        def metadata = getMetadata()
        setMetadata(metadata, pauseRollouts, deployments)
    }

    /**
     * Provides mandatory metadata that should not be overridden by the user.
     * This metadata is expected, instead, to override user metadata.
     *
     * @return a <code>Map</code> with mandatory metadata for the component.
     * @throws IllegalArgumentException if a invalid chart file is found.
     */
    def getMandatoryMetadata() {
        def metadata = [
            componentId:         context.componentId,
            managedBy:           'tailor',
            chartNameAndVersion: null as Object,
            projectId:           context.projectId,
        ]

        // Find out whether this component is managed by helm
        def chart = getChartNameAndVersion()
        if (chart) {
            metadata.managedBy = 'Helm'
            metadata.chartNameAndVersion = chart
        }

        // When triggered by the release manager, it provides some of the metadata.
        if (context.triggeredByOrchestrationPipeline) {
            metadata.putAll([
                systemName:     steps.env?.BUILD_PARAM_CONFIGITEM,
                projectVersion: steps.env?.BUILD_PARAM_CHANGEID,
                workInProgress: steps.env?.BUILD_PARAM_VERSION == 'WIP',
            ])
        } else {
            // For the moment, we don't allow the users to customize these labels
            metadata.putAll([
                systemName:     null,
                projectVersion: null,
                workInProgress: null,
            ])
        }

        return metadata
    }

    /**
     * Sanitize all metadata values to make sure they are valid label values.
     * Valid label values must be 63 characters or less and must be empty
     * or begin and end with an alphanumeric character ([a-z0-9A-Z])
     * with dashes (-), underscores (_), dots (.), and alphanumerics between.
     * If an illegal value is found for an entry that allows modifications, the value will be sanitized as follows:
     * 1. Any non-alphanumeric characters will be removed from the beginning of the value.
     * 2. If it's longer than 63 characters, the trailing characters after the 63rd will be removed.
     * 3. Any non-alphanumeric characters will be removed from the end of the value.
     * 4. Every remaining illegal character will be replaced with an underscore.
     *
     * NOTE: If, after step 1, the value is empty, an exception will be risen
     * instead of silently assigning an empty value. This situation should be rare, only for non-empty values
     * consisting only of non-alphanumeric characters.
     *
     * If an illegal value is found for an entry that does not allow modifications,
     * an exception with an informative message will be risen, thus ending the labelling process.
     *
     * All values are converted to strings using the <code>toString()</code> method.
     *
     * @param metadata a <Map> with the metadata entries to validate.
     * @return the metadata with <code>String</code>, possibly sanitized, values.
     * @throws IllegalArgumentException if an illegal value is found for an entry that does not allow modifications
     * or a value is found that consists entirely in non-alphanumeric characters.
     */
    private static sanitizeValues(metadata) {
        return (Map<String, String>) metadata.collectEntries { key, value ->
            if (value == null) {
                return [(key): null]
            }
            def stringValue = value.toString()
            if (!stringValue) {
                return [(key): stringValue]
            }
            def sanitizedValue = stringValue
            def end = sanitizedValue.length()
            def i = 0
            while (i < end && !Character.isLetterOrDigit(sanitizedValue.charAt(i))) {
                i++
            }
            if (i == end) {
                throw new IllegalArgumentException('Metadata entries must not entirely consist of ' +
                    "non-alphanumeric characters. Please, check the metadata.yml file: ${key}=${value}")
            }
            // Now the value is warranted to contain, at least, one alphanumeric character, at position i.
            def j = Math.min(end, i + 63)
            // No guard needed.
            while (!Character.isLetterOrDigit(sanitizedValue.charAt(j - 1))) {
                j--
            }
            if (i > 0 || j < end) {
                sanitizedValue = sanitizedValue.subSequence(i, j)
            }
            def matcher = sanitizedValue =~ LABEL_VALUE_PATTERN
            sanitizedValue = matcher.replaceAll('_')
            if (sanitizedValue != stringValue && strictEntries.contains(key)) {
                throw new IllegalArgumentException('Illegal value for metadata entry. ' +
                    'Values must be 63 characters or less, begin and end with an alphanumeric character and ' +
                    "contain only alphanumerics, '-', '_' and '.'. Please, check the metadata.yml file: " +
                    "${key}=${value}")
            }
            return [(key): sanitizedValue]
        }
    }

    /**
     * Retrieves metadata for the component.
     * All metadata values are warranted to be valid strings to be used as label values.
     * Any non-string value is converted to a string and illegal label values are
     * sanitized, if the entry allows modifications.
     *
     * @return a <code>Map</code> with the metadata for the component.
     * @throws IllegalArgumentException if an entry value consists entirely in non-alphanumeric characters
     * or it is not a valid label value and the entry does not allow modifications
     * or some other invalid metadata was found.
     */
    private getMetadata() {
        def metadata = getDefaultMetadata()
        metadata.putAll(getComponentMetadata())
        metadata.putAll(getMandatoryMetadata())
        metadata = sanitizeValues(metadata)
        if (metadata.name == metadata.componentId) {
            metadata.remove('componentId')
        }
        return metadata
    }

    /**
     * Sets the suitable labels and annotations to the component resource from the given metadata.
     * It will only use entries whose keys are one of the values in the <code>labelToMetadataMapping Map</code>.
     *
     * @param metadata a <code>Map</code> with the metadata to use to set the labels.
     * @param pauseRollouts whether to pause rollouts to avoid triggering deployments when updating the labels
     * in <code>dc</code> and <code>deploy</code> templates.
     * @throws IllegalArgumentException if the target OpenShift project cannot be guessed from the available data.
     * @throws RuntimeException if there is an error setting the labels and annotations in OpenShift.
     */
    private setMetadata(metadata, pauseRollouts = false, deployments = null) {
        // TODO Make sure the user cannot override the labels set by the release manager in a previous deployment.
        def labels = getLabels(metadata)
        def project = getTargetProject()
        labelResources(project, labels)
        applyLabelsToDeploymentTemplates(project, labels, pauseRollouts, deployments)
    }

    /**
     * Provides default metadata values, which may be overridden by the component configuration.
     *
     * @return a <code>Map</code> with the default metadata for the component.
     */
    private getDefaultMetadata() {
        def metadata = [
            name: context.componentId,
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
     * Builds a map with the labels to be set or unset, based on the given metadata.
     * The map keys and values are the corresponding label keys and values.
     * A <code>null</code> value is used to mark a label that has to be removed.
     *
     * @param metadata a map with the metadata to generate labels for.
     * @return a map with the labels to be set or removed.
     */
    private getLabels(metadata) {
        // TODO Make sure the user cannot override the labels set by the release manager in a previous deployment.
        def labels = labelKeys.findAll { key, value ->
            removableKeys.contains(key) || metadata[labelToMetadataMapping[key]] != null
        }.collectEntries { key, value ->
            [(value): metadata[labelToMetadataMapping[key]]]
        }
        return labels
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
     * Updates the labels defined in the Deployment and DeploymentConfig templates
     * by adding, replacing or removing them with the ones given.
     * It can optionally pause rollouts at the same time, so that other modifications can be performed
     * before triggering a redeploy.
     *
     * @param project the namespace whose objects will be updated.
     * @param labels a map with the label keys and values to set or remove. Null values mean the label will be removed.
     * @param pauseRollouts Whether to pause rollouts. By default, a rollout will be triggered when labels are modified.
     * @throws RuntimeException if there is an error updating the deployment templates.
     */
    private applyLabelsToDeploymentTemplates(project, labels, pauseRollouts = false, deployments = null) {
        def patch = [template: [metadata: [labels: labels]]]
        if (pauseRollouts != null) {
            // Setting paused to null will remove it, which has the effect of resuming rollouts.
            patch.paused = pauseRollouts ?: null
        }
        if (deployments != null) {
            openShift.bulkPatch(project, deployments, patch, '/spec')
        } else {
            openShift.bulkPatch(project, ['dc', 'deploy'], config.selector, patch, '/spec')
        }
    }

    /**
     * Sets or removes labels to all the resources selected by <code>config.selector</config>.
     *
     * @param project the project in which resources are to be labeled.
     * @param labels a map with the labels to apply or remove. A null value means the label will be removed.
     */
    private labelResources(project, labels) {
        openShift.labelResources(project, 'all', labels, config.selector)
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
        return readYaml('metadata.yml')
    }

    /**
     * Reads the default metadata for components provisioned from the current quickstarter
     * from the quickstarter repository.
     *
     * @return a <code>Map</code> with the default metadata for components provisioned with the current quickstarter.
     */
    private getMetadataFromQuickStarter() {
        return readYaml("${context.componentId}/metadata.yml")
    }

    /**
     * Searches for a helm main chart file in the component.
     * If found, it determines the chart name and version string,
     * with format name-version.
     * It replaces any '+' symbol in the version with a '_' symbol.
     *
     * @return the chart name-version string, or null if no chart file can be found.
     * @throws IllegalArgumentException if the chart file does not have name or version.
     */
    private getChartNameAndVersion() {
        if (config.chartDir) {
            def chart = readYaml("${config.chartDir}/Chart.yaml")
            if (chart != null) {
                if (!chart.name || !chart.version) {
                    throw new IllegalArgumentException(
                        "Invalid chart file ${config.chartDir}/Chart.yaml: name=${chart.name}, version=${chart.version}"
                    )
                }
                // The following replacement is as per the specification of this label
                // and independent of the way we choose to sanitize labels otherwise.
                return "${chart.name}-${chart.version.replace('+' as char, '_' as char)}"
            }
        }
        return null
    }

    /**
     * Reads the YAML file given in <code>file</code> and returns the contents in <code>Map</code> format.
     * Returns null, if the file cannot be found.
     *
     * @param file a YAML file to read metadata from.
     * @return a <code>Map</code> with the metadata contained in the given <code>file</code> or null if not found.
     */
    private readYaml(file) {
        if (steps.fileExists(file)) {
            return steps.readYaml(file: file)
        }
        return null
    }

}
