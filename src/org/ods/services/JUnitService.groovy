package org.ods.services

import org.ods.component.ILogger

class JUnitService {

  private def script
  private ILogger logger
  private static final String XUNIT_SYSTEM_RESULT_DIR = "build/test-results/test"
  
  JUnitService(script, ILogger logger) {
    this.script = script
    this.logger = logger
  }
  
  void stashTestResults(String customXunitResultsDir, String stashNamePostFix = "stash") {
    customXunitResultsDir = (customXunitResultsDir && customXunitResultsDir.trim().length() > 0) ?: "build/test-results/test
    logger.info "Stashing testResults from ${customXunitResultsDir}"
    script.sh(
      script: "mkdir -p ${XUNIT_SYSTEM_RESULT_DIR} ${customXunitResultsDir} && cp -rf ${customXunitResultsDir}/* ${XUNIT_SYSTEM_RESULT_DIR} | true", 
      label: "Moving test results to system location: ${XUNIT_SYSTEM_RESULT_DIR}")

    def foundTests = script.sh(script: "ls -la ${XUNIT_SYSTEM_RESULT_DIR}/*.xml | wc -l", 
      returnStdout: true, label: "Counting test results in ${XUNIT_SYSTEM_RESULT_DIR}").trim()
      
    logger.info "Found ${foundTests} tests in '${XUNIT_SYSTEM_RESULT_DIR}'"

    context.addArtifactURI("testResultsFolder", XUNIT_SYSTEM_RESULT_DIR)
    context.addArtifactURI("testResults", foundTests)

    script.junit (testResults: "${XUNIT_SYSTEM_RESULT_DIR}/**/*.xml", allowEmptyResults : true)
    
    if (foundTests.toInteger() == 0) {
      logger.debug "ODS Build did fail, and no test results,.. returning"
      return
    }

    // stash them in the mro pattern
    script.stash(name: "test-reports-junit-xml-${stashNamePostFix}", 
      includes: '${XUNIT_SYSTEM_RESULT_DIR}/*.xml', allowEmpty: true)
  }

}
