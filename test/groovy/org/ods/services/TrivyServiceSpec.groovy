package org.ods.services

import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class TrivyServiceSpec extends PipelineSpockTestBase {

    def "scan - SUCCESS"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(new Logger(steps, false))
        def service = Spy(TrivyService, constructorArgs: [steps, logger])

        when:
        def result = service.scan("component1", "vuln,misconfig,secret,license", "os,library",
           "cyclonedx", "--debug --timeout=10m", "trivy-sbom.json", "docker-group-ods", "openshift-domain.com")

        then:
        1 * steps.sh(_) >> {
            assert it.label == ['Scan via Trivy CLI']
            assert it.returnStatus == [true]
            assert it.script.toString().contains('set +e &&')
            assert it.script.toString().contains('trivy fs')
            assert it.script.toString().contains('--db-repository docker-group-ods.openshift-domain.com/aquasecurity/trivy-db')
            assert it.script.toString().contains('--java-db-repository docker-group-ods.openshift-domain.com/aquasecurity/trivy-java-db')
            assert it.script.toString().contains('--cache-dir /tmp/.cache')
            assert it.script.toString().contains('--scanners vuln,misconfig,secret,license')
            assert it.script.toString().contains('--pkg-types os,library')
            assert it.script.toString().contains('--format cyclonedx')
            assert it.script.toString().contains('--output trivy-sbom.json')
            assert it.script.toString().contains('--license-full')
            assert it.script.toString().contains('--debug --timeout=10m')
            assert it.script.toString().contains('. &&')
            assert it.script.toString().contains('set -e')

            return 0
        }
        1 * steps.sh(_) >> {
            assert it.label == ['Read SBOM with Trivy CLI']
            assert it.returnStatus == [true]
            assert it.script.toString().contains('set +e &&')
            assert it.script.toString().contains('trivy sbom')
            assert it.script.toString().contains('--cache-dir /tmp/.cache')
            assert it.script.toString().contains('trivy-sbom.json &&')
            assert it.script.toString().contains('set -e')

            return 0
        }
        1 * logger.info("Finished scan via Trivy CLI successfully!")
        0 == result
    }

    def "scan - Operational Error"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(new Logger(steps, false))
        def service = Spy(TrivyService, constructorArgs: [steps, logger])

        when:
        def result = service.scan("component1", "vuln,misconfig,secret,license", "os,library",
           "cyclonedx", "", "trivy-sbom.json", "docker-group-ods", "openshift-domain.com")

        then:
        1 * steps.sh({ it.label == 'Scan via Trivy CLI' }) >> 1
        1 * steps.sh({ it.label == 'Read SBOM with Trivy CLI' }) >> 0
        1 * logger.info(
                  "An error occurred while processing the Trivy scan request " +
                  "(e.g. invalid command line options, operational error, or " +
                  "severity threshold exceeded when using the --exit-code flag)."
        )
        1 == result
    }

    def "scan - Unknown return code"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def logger = Spy(new Logger(steps, false))
        def service = Spy(TrivyService, constructorArgs: [steps, logger])

        when:
        def result = service.scan("component1", "vuln,misconfig,secret,license", "os,library",
           "cyclonedx", "", "trivy-sbom.json", "docker-group-ods", "openshift-domain.com")

        then:
        1 * steps.sh({ it.label == 'Scan via Trivy CLI' }) >> 127
        1 * steps.sh({ it.label == 'Read SBOM with Trivy CLI' }) >> 0
        1 * logger.info("An unknown return code was returned: 127")
        127 == result
    }

}
