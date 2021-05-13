package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class ScanWithSnykOptions extends Options {

    /**
     * Required! Authentication token of a service account within your organisation. */
    String snykAuthenticationCode

    /**
     * Whether to fail the build when vulnerabilities are found. Defaults to `true`. */
    boolean failOnVulnerabilities

    /**
     * Name of the Snyk organisation. Default to `context.projectId`. */
    String organisation

    /**
     * Name of the Snyk project name. Default to `context.componentId`. */
    String projectName

    /**
     * Build file from which to gather dependency information. Defaults to `build.gradle`. */
    String buildFile

    /**
     * Severity threshold for failing. If any found vulnerability has a severity
     * equal or higher to the threshold, the snyk test will return with a
     * failure status. Possible values are `low`, `medium`, `high`.
     * Defaults to `low`. */
    String severityThreshold

    /**
     * Additional flags for the Snyk CLI. Please refer to the official Snyk CLI
     * reference for possible options and don't forget to take the CLI version
     * of your ODS installation into account. The value of `additionalFlags`
     * must be a list in which the entries have the official flag name and a
     * possible value.
     * Example: `['--all-sub-projects', '--show-vulnerable-paths=all']` */
    List<String> additionalFlags

}
