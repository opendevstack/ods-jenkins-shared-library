package vars.test_helper

import groovy.json.JsonSlurper
import com.lesfurets.jenkins.unit.BasePipelineTest
import org.ods.component.IContext
import org.ods.util.ILogger
import spock.lang.Specification
import org.ods.services.ServiceRegistry

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
        helper.registerAllowedMethod('readJSON', [ Map ]) { Map args -> new JsonSlurper().parseText(args.text) }
        // we register our custom groovy method withStage so that is is available
        // in every script executed by the Jenkins Pipeline Unit testing framework
        helper.registerAllowedMethod("withStage", [String, Object, ILogger, Closure], { String stageLabel, Object context, ILogger logger, Closure closure ->
          return loadScript('vars/withStage.groovy').call(stageLabel, context, logger, closure)
        })    
        helper.registerAllowedMethod("ansiColor", [String, Closure], { String color, Closure block -> 
          block ()
        })
        helper.registerAllowedMethod("containerTemplate", [ Map ], { Map template -> 
          return template
        })
        helper.registerAllowedMethod("findFiles", [ Map ], { Map files -> 
          return files
        })
        helper.registerAllowedMethod("podTemplate", [ Map, Closure ], { Map template, Closure block -> 
          block ()
        })
        helper.registerAllowedMethod('podAnnotation', [ Map ]) {}
        helper.registerAllowedMethod('emailext', [ Map ]) {}
        helper.registerAllowedMethod('wrap', [ Map, Closure ], { Map attributes, Closure block -> 
          block ()
        }) 
        helper.registerAllowedMethod('junit', [ Map ]) {}
      }
    }
    basePipelineTest.setUp()
    ServiceRegistry.instance.clear()
  }

  protected String readResource(String name) {
    def classLoader = getClass().getClassLoader();
    def file = new File(classLoader.getResource(name).getFile());
    file.text
  }
}
