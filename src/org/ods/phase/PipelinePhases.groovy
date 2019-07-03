package org.ods.phase

class PipelinePhases {
    static final String BUILD_PHASE   = "Build"
    static final String DEPLOY_PHASE  = "Deploy"
    static final String TEST_PHASE    = "Test"
    static final String RELEASE_PHASE = "Release"

    static final List ALWAYS_PARALLEL_PHASES = [ BUILD_PHASE ]
}
