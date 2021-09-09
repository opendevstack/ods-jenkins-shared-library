package org.ods.core.test

import com.lesfurets.jenkins.unit.RegressionTest
import com.lesfurets.jenkins.unit.cps.BasePipelineTestCPS
import spock.lang.Specification

/**
 * Class base to create component tests
 */
class PipelineSpecBase extends Specification implements RegressionTest {

    /**
     * Delegate to the junit cps transforming base test
     */
    @Delegate
    BasePipelineTestCPS baseTest

    def setup() {
        baseTest = new BasePipelineTestCPS()
        baseTest.setUp()
    }
}
