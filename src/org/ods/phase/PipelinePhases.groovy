package org.ods.phase

class PipelinePhases {
    static final String BUILD_PHASE   = 'build'
    static final String DEPLOY_PHASE  = 'deploy'
    static final String TEST_PHASE    = 'test'
    static final String RELEASE_PHASE = 'release'

    static final List ALWAYS_PARALLEL_PHASES = [ BUILD_PHASE ]
}
