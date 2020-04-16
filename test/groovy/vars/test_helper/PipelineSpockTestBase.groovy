package vars.test_helper

import com.lesfurets.jenkins.unit.BasePipelineTest
import org.ods.component.IContext
import spock.lang.Specification

/**
 * A base class for Spock testing using the Jenkins Pipeline Unit testing framework (https://github.com/jenkinsci/JenkinsPipelineUnit)
 */
class PipelineSpockTestBase extends Specification {

  @Delegate
  BasePipelineTest basePipelineTest

  def setup() {
    // create instance of abstract class BasePipelineTest by creating an anonymous class
    basePipelineTest = new BasePipelineTest() {
      @Override
      void registerAllowedMethods() {
        super.registerAllowedMethods()
        // we register our custom groovy method withStage so that is is available
        // in every script executed by the Jenkins Pipeline Unit testing framework
        helper.registerAllowedMethod("withStage", [String, IContext, Closure], { String stageLabel, IContext context, Closure closure ->
          return loadScript('vars/withStage.groovy').call(stageLabel, context, closure)
        })
      }
    }
    basePipelineTest.setUp()
  }
}
