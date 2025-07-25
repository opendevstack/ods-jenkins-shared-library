package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class ScanWithSonarOptions extends Options {

    /**
     * Branches to scan.
     * Example: `['master', 'develop']`.
     * Next to exact matches, it also supports prefixes (e.g. `feature/`) and \
     * all branches (`*`).
     * Defaults to `master` for the community edition of SonarQube, and `*` for
     * all other editions (which are capable of handling multiple branches). */
    List<String> branches

    /**
     * Branch to scan.
     * Example: `'master'`.
     * Next to exact matches, it also supports prefixes (e.g. `feature/`) and all branches (`*`). */
    String branch

    /**
     * Whether to fail the build if the quality gate defined in the SonarQube
     * project is not reached. Defaults to `false`. */
    boolean requireQualityGatePass

    /**
     * Whether to analyze pull requests and decorate them in Bitbucket. Turned
     * on by default, however a scan is only performed if the `branch` property
     * allows it. */
    boolean analyzePullRequests

    /**
     * Branch(es) for which no PR analysis should be performed. If not set, it
     * will be extracted from  `branchToEnvironmentMapping` of the `context`. */
    List<String> longLivedBranches

    /**
     * Name of `BuildConfig`/`ImageStream` of the image that we want to scan (defaults to `context.componentId`).
     * BuildOpenShiftImageStage puts the imageRef into a map with the `resourceName` as key.
     * In order to be able to receive the imageRef for scanning, the `resourceName` needs
     * to be the same as in BuildOpenShiftImageStage. */
    String resourceName
    /**
     * Patterns to exclude from SonarQube scan.
     * Example: `'**/test/**,**/docs/**'`.
     */
    String exclusions

}
