package org.ods.component

import org.ods.util.ILogger
import org.ods.util.PodData
import spock.lang.Specification

class ECROnlyDeploymentStrategySpec extends Specification {

    IContext context
    IImageRepository imageRepository
    ILogger logger

    def setup() {
        context = Stub(IContext) {
            getTargetProject() >> 'foo-dev'
            getComponentId() >> 'component-a'
        }
        imageRepository = Mock(IImageRepository)
        logger = Mock(ILogger)
    }

    private ECROnlyDeploymentStrategy createStrategy(Map<String, Object> config = [:]) {
        // Apply minimal config defaults so RolloutOpenShiftDeploymentOptions can be constructed
        if (!config.containsKey('selector')) { config.selector = 'app=component-a' }
        if (!config.containsKey('imageTag')) { config.imageTag = 'abc12345' }
        if (!config.containsKey('helmValues')) { config.helmValues = [:] }
        if (!config.containsKey('helmValuesFiles')) { config.helmValuesFiles = ['values.yaml'] }
        if (!config.containsKey('helmEnvBasedValuesFiles')) { config.helmEnvBasedValuesFiles = [] }
        if (!config.containsKey('helmDefaultFlags')) { config.helmDefaultFlags = ['--install', '--atomic'] }
        if (!config.containsKey('helmAdditionalFlags')) { config.helmAdditionalFlags = [] }
        if (!config.containsKey('helmDiff')) { config.helmDiff = true }
        new ECROnlyDeploymentStrategy(context, config, imageRepository, logger)
    }

    def "deploy retags images and returns empty rollout data"() {
        given:
        context.getBuildArtifactURIs() >> [builds: [image1: [:], image2: [:]]]
        def strategy = createStrategy()

        when:
        Map<String, List<PodData>> result = strategy.deploy()

        then:
        1 * imageRepository.retagImages('foo-dev', { it.containsAll(['image1', 'image2']) && it.size() == 2 }, 'abc12345', 'abc12345')
        result == [:]
    }

    def "deploy uses namespaceOverride when set"() {
        given:
        context.getBuildArtifactURIs() >> [builds: [myapp: [:]]]
        def strategy = createStrategy([helmValues: [namespaceOverride: 'custom-ns']])

        when:
        Map<String, List<PodData>> result = strategy.deploy()

        then:
        1 * imageRepository.retagImages('custom-ns', _ as Set, 'abc12345', 'abc12345')
        result == [:]
    }

    def "deploy handles empty builds"() {
        given:
        context.getBuildArtifactURIs() >> [builds: [:]]
        def strategy = createStrategy()

        when:
        Map<String, List<PodData>> result = strategy.deploy()

        then:
        1 * imageRepository.retagImages('foo-dev', { it.isEmpty() }, 'abc12345', 'abc12345')
        result == [:]
    }

    def "deploy filters out imported images"() {
        given:
        context.getBuildArtifactURIs() >> [builds: ['myapp': [:], 'imported-base': [:]]]
        def strategy = createStrategy()

        when:
        strategy.deploy()

        then:
        1 * imageRepository.retagImages('foo-dev', { it.contains('myapp') && !it.contains('imported-base') && it.size() == 1 }, _, _)
    }
}
