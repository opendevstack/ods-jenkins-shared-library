package org.ods.services

import org.ods.util.Logger
import vars.test_helper.PipelineSpockTestBase

class TrivyServiceSpec extends PipelineSpockTestBase {

    def "invoke Trivy cli"() {
        given:
        def steps = Spy(util.PipelineSteps)
        def service = Spy(TrivyService, constructorArgs: [
            steps,
            new Logger(steps, false)
        ])

        when:
        def result = service.scanViaCli("vuln,config,secret,license", "os,library",
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
            assert it.script.toString().contains('--scanners vuln,config,secret,license')
            assert it.script.toString().contains('--vuln-type os,library')
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

        0 == result
    }

}
