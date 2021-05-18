package org.ods.openshift

import org.ods.services.OpenShiftService
import org.ods.util.IPipelineSteps
import org.ods.util.Logger
import spock.lang.Unroll
import util.PipelineSteps
import util.SpecHelper

class OpenShiftResourceMetadataSpec extends SpecHelper {
    private static final metadata0 = '''
        description: willBeIgnored
        supplier: willBeIgnored
    '''

    private static final labels0 = [
        'app.kubernetes.io/name':           'testComponent',
        'app.kubernetes.io/managed-by':     'tailor',
        'app.kubernetes.io/instance':       null,
        'app.kubernetes.io/part-of':        null,
        'app.openshift.io/runtime':         null,
        'app.openshift.io/runtime-version': null,
        'helm.sh/chart':                    null,
        'app.opendevstack.org/project':     'testProject',
    ]

    private static final metadata1 = '''
        type: ods
        name: ._-,:;@ The quick+brown fox$jumps-over the lazy dog._The quick  brown fox jumps over the lazy dog.
        version: 4.00
        role: someRole
        partOf: someBigApp
        runtime: spring-boot
        runtimeVersion: springBootVersion
        systemName: willBeIgnored
        project: willBeOverridden
        projectVersion: willBeIgnored
        chart: willBeIgnored
        componentId: willBeOverridden
        managedBy: willBeOverridden
    '''

    private static final labels1 = [
        'app.kubernetes.io/name':               'The_quick_brown_fox_jumps-over_the_lazy_dog._The_quick__brown_f',
        'app.kubernetes.io/version':            '4.0',
        'app.kubernetes.io/managed-by':         'tailor',
        'app.opendevstack.org/type':            'ods',
        'app.kubernetes.io/component':          'someRole',
        'app.kubernetes.io/part-of':            'someBigApp',
        'app.openshift.io/runtime':             'spring-boot',
        'app.openshift.io/runtime-version':     'springBootVersion',
        'app.kubernetes.io/instance':           'testComponent',
        'helm.sh/chart':                        null,
        'app.opendevstack.org/project':         'testProject',
    ]

    private static final metadata2 = '''
        name: -_.testComponent_.-
        componentId: willBeOverridden
        managedBy: willBeOverridden
    '''

    private static final labels2 = [
        'app.kubernetes.io/name':           'testComponent',
        'app.kubernetes.io/managed-by':     'tailor',
        'app.kubernetes.io/instance':       null,
        'app.kubernetes.io/part-of':        null,
        'app.openshift.io/runtime':         null,
        'app.openshift.io/runtime-version': null,
        'helm.sh/chart':                    null,
        'app.opendevstack.org/project':     'testProject',
    ]

    private static final metadata3 = '''
        name: .-_ ?@The quick+brown fox$jumps-over the lazy dog._The quick  brow.-_ox jumps over the lazy dog.
        componentId: willBeOverridden
        managedBy: willBeOverridden
    '''

    private static final labels3 = [
        'app.kubernetes.io/name':           'The_quick_brown_fox_jumps-over_the_lazy_dog._The_quick__brow',
        'app.kubernetes.io/managed-by':     'tailor',
        'app.kubernetes.io/component':      'frontend',
        'app.kubernetes.io/instance':       'testComponent',
        'app.kubernetes.io/part-of':        null,
        'app.openshift.io/runtime':         null,
        'app.openshift.io/runtime-version': null,
        'helm.sh/chart':                    null,
        'app.opendevstack.org/project':     'testProject',
    ]

    private static final metadata4 = '''
        componentId: willBeOverridden
        managedBy: willBeOverridden
    '''

    private static final labels4 = [
        'app.kubernetes.io/name':           'testComponent',
        'app.kubernetes.io/managed-by':     'tailor',
        'app.kubernetes.io/instance':       null,
        'app.kubernetes.io/part-of':        null,
        'app.openshift.io/runtime':         null,
        'app.openshift.io/runtime-version': null,
        'helm.sh/chart':                    null,
        'app.opendevstack.org/project':     'testProject',
    ]

    private static final metadata5 = '''
        componentId: willBeOverridden
        managedBy: willBeOverridden
    '''

    private static final labels5 = [
        'app.kubernetes.io/name':           'testComponent',
        'app.kubernetes.io/managed-by':     'tailor',
        'app.kubernetes.io/component':      'backend',
        'app.kubernetes.io/instance':       null,
        'app.kubernetes.io/part-of':        null,
        'app.openshift.io/runtime':         null,
        'app.openshift.io/runtime-version': null,
        'helm.sh/chart':                    null,
        'app.opendevstack.org/project':     'testProject',
    ]

    private static final metadata6 = '''
        chart: willBeOverridden
        componentId: willBeOverridden
        managedBy: willBeOverridden
    '''

    private static final labels6 = [
        'app.kubernetes.io/name':           'testComponent',
        'app.kubernetes.io/managed-by':     'helm',
        'app.kubernetes.io/instance':       null,
        'app.kubernetes.io/part-of':        null,
        'app.openshift.io/runtime':         null,
        'app.openshift.io/runtime-version': null,
        'helm.sh/chart':                    'myChart-1.0_10',
        'app.opendevstack.org/project':     'testProject',
    ]

    private static final metadata7 = '''
        componentId: willBeOverridden
        managedBy: willBeOverridden
        systemName: willBeOverridden
        projectVersion: willBeOverridden
    '''

    private static final labels7 = [
        'app.kubernetes.io/name':                'testComponent',
        'app.kubernetes.io/managed-by':          'tailor',
        'app.kubernetes.io/instance':            null,
        'app.kubernetes.io/part-of':             null,
        'app.openshift.io/runtime':              null,
        'app.openshift.io/runtime-version':      null,
        'helm.sh/chart':                         null,
        'app.opendevstack.org/system-name':      'mySystem',
        'app.opendevstack.org/project-version':  '1.0',
        'app.opendevstack.org/work-in-progress': 'true',
        'app.opendevstack.org/project':          'testProject',
    ]

    private static final metadata8 = '''
        chart: willBeOverridden
        componentId: willBeOverridden
        managedBy: willBeOverridden
        systemName: willBeOverridden
        projectVersion: willBeOverridden
    '''

    private static final labels8 = [
        'app.kubernetes.io/name':                'testComponent',
        'app.kubernetes.io/managed-by':          'helm',
        'app.kubernetes.io/instance':            null,
        'app.kubernetes.io/part-of':             null,
        'app.openshift.io/runtime':              null,
        'app.openshift.io/runtime-version':      null,
        'helm.sh/chart':                         'myChart-1.0_10',
        'app.opendevstack.org/system-name':      'mySystem',
        'app.opendevstack.org/project-version':  '1.0',
        'app.opendevstack.org/work-in-progress': 'false',
        'app.opendevstack.org/project':          'testProject',
    ]

    @Unroll
    def "metadata is correctly assigned"(
        String quickstarter,
        String version,
        boolean helm,
        String metadata,
        Map<String, String> labels
    ) {
        given:
        def steps = Stub(IPipelineSteps)
        def logger = new Logger(steps, false)
        def openShift = Mock(OpenShiftService)
        def projectId = 'testProject'
        def componentId = 'testComponent'
        def environment = 'dev'
        def targetProject = "${projectId}-${environment}".toString()
        def selector = "app=${projectId}-${componentId}".toString()

        when:
        def context = {
            def ctx = [
                projectId:   projectId,
                componentId: componentId,
            ]
            if (quickstarter) {
                ctx.sourceDir = quickstarter
            } else {
                ctx.triggeredByOrchestrationPipeline = version != null
                ctx.targetProject = targetProject
            }
            return ctx
        }
        def config = {
            def cfg = [
                selector: selector
            ]
            if (quickstarter) {
                cfg.environment = environment
            } else {
                cfg.chartDir = 'chart'
            }
            return cfg
        }
        if (version) {
            steps.getEnv() >> [
                BUILD_PARAM_CONFIGITEM: 'mySystem',
                BUILD_PARAM_CHANGEID:   '1.0',
                BUILD_PARAM_VERSION:    version,
            ]
        }
        steps.fileExists(quickstarter ? "${componentId}/metadata.yml" : 'metadata.yml') >> true
        steps.fileExists('chart/Chart.yaml') >> helm
        steps.readYaml(file: 'chart/Chart.yaml') >> [name: 'myChart', version: '1.0+10']
        steps.readYaml(_ as Map) >> { Map args ->
            def testSteps = new PipelineSteps()
            return testSteps.readYaml(args) { String file ->
                def data = null
                if (steps.fileExists(file)) {
                    data = testSteps.readYaml(text: metadata)
                }
                return data
            }
        }
        def metadataTool = new OpenShiftResourceMetadata(steps, context(), config(), logger, openShift)
        metadataTool.updateMetadata()

        then:
        1 * openShift.labelResources(targetProject, 'all', labels, selector)

        where:
        quickstarter         | version | helm  | metadata  || labels
        null                 | null    | false | metadata0 || labels0
        null                 | null    | false | metadata1 || labels1
        'be-java-springboot' | null    | false | metadata1 || labels1
        null                 | null    | false | metadata2 || labels2
        'fe-angular'         | null    | false | metadata3 || labels3
        'be-fe-mono-repo'    | null    | false | metadata4 || labels4
        'someQuickstarter'   | null    | false | metadata4 || labels4
        'be-java-springboot' | null    | false | metadata5 || labels5
        null                 | null    | true  | metadata6 || labels6
        null                 | 'WIP'   | false | metadata7 || labels7
        null                 | '1.0'   | true  | metadata8 || labels8
    }

    @Unroll
    def "fails without target project"() {
        given:
        def steps = Stub(IPipelineSteps)
        def logger = new Logger(steps, false)
        def openShift = Mock(OpenShiftService)
        def projectId = 'testProject'
        def componentId = 'testComponent'
        def selector = "app=${projectId}-${componentId}"

        when:
        def context = [
            projectId:                        projectId,
            componentId:                      componentId,
            triggeredByOrchestrationPipeline: false,
        ]
        def config = [
            selector: selector,
            chartDir: 'chart',
        ]
        steps.fileExists('metadata.yml') >> true
        steps.fileExists(_ as String) >> false
        steps.readYaml(file: 'metadata.yml') >> [:]
        def metadataTool = new OpenShiftResourceMetadata(steps, context, config, logger, openShift)
        metadataTool.updateMetadata()

        then:
        thrown IllegalArgumentException
    }

    @Unroll
    def "fails with non-alphanumeric values"() {
        given:
        def steps = Stub(IPipelineSteps)
        def logger = new Logger(steps, false)
        def openShift = Mock(OpenShiftService)
        def projectId = 'testProject'
        def componentId = 'testComponent'
        def selector = "app=${projectId}-${componentId}"

        when:
        def context = [
            projectId:                        projectId,
            componentId:                      componentId,
            triggeredByOrchestrationPipeline: false,
            targetProject:                    "${projectId}-dev"
        ]
        def config = [
            selector: selector,
            chartDir: 'chart',
        ]
        steps.fileExists('metadata.yml') >> true
        steps.fileExists(_ as String) >> false
        steps.readYaml(file: 'metadata.yml') >> [ name: '@ _-.' ]
        def metadataTool = new OpenShiftResourceMetadata(steps, context, config, logger, openShift)
        metadataTool.updateMetadata()

        then:
        thrown IllegalArgumentException
    }

    @Unroll
    def "fails with illegal values for unmodifiable metadata entries"() {
        given:
        def steps = Stub(IPipelineSteps)
        def logger = new Logger(steps, false)
        def openShift = Mock(OpenShiftService)
        def projectId = 'testProject'
        def componentId = 'testComponent'
        def selector = "app=${projectId}-${componentId}"
        def context = [
            projectId:                        projectId,
            componentId:                      componentId,
            triggeredByOrchestrationPipeline: false,
            targetProject:                    "${projectId}-dev",
            triggeredByOrchestrationPipeline: true,
        ]
        def config = [
            selector: selector,
            chartDir: 'chart',
        ]
        steps.fileExists('metadata.yml') >> true
        steps.fileExists(_ as String) >> false
        steps.readYaml(file: 'metadata.yml') >> [:]
        def metadataTool

        when:
        steps.getEnv() >> [
            BUILD_PARAM_CONFIGITEM: 'mySystem',
            BUILD_PARAM_CHANGEID:   '1+0',
            BUILD_PARAM_VERSION:    'WIP',
        ]
        metadataTool = new OpenShiftResourceMetadata(steps, context, config, logger, openShift)
        metadataTool.updateMetadata()

        then:
        thrown IllegalArgumentException

        when:
        steps.getEnv() >> [
            BUILD_PARAM_CONFIGITEM: 'my+System',
            BUILD_PARAM_CHANGEID:   '1.0',
            BUILD_PARAM_VERSION:    '1.0',
        ]
        metadataTool = new OpenShiftResourceMetadata(steps, context, config, logger, openShift)
        metadataTool.updateMetadata()

        then:
        thrown IllegalArgumentException
    }
}
