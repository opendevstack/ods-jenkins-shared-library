package org.ods.service

import org.ods.Logger
import org.ods.OdsContext
import org.ods.PipelineScript
import spock.lang.Specification

class OpenShiftServiceSpec extends Specification {

  private PipelineScript script = new PipelineScript()
  private Logger logger = Mock(Logger)

  def "extract build ID from startBuildInfo"() {
    given:
    def config = [
        componentId: 'foo',
    ]
    def context = new OdsContext(script, config, logger)
    def openShiftService = new OpenShiftService(script, context)

    when:
    def buildId = openShiftService.extractBuildId('foo-123')

    then:
    buildId != null

    when:
    buildId = openShiftService.extractBuildId('foo')

    then:
    buildId == null
  }

}
