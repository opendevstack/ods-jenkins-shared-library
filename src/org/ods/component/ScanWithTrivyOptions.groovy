package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class ScanWithTrivyOptions extends Options {

    /**
     * Name of component that we want to scan. Defaults to `context.componentId`. */
    String resourceName

    /**
     * Set the format for the generated report. Defaults to `cyclonedx`. */
    String format

    /**
     * Comma-separated list of what security issues to detect. Defaults to `vuln,config,secret,license`. */
    String scanners

    /**
     * Comma-separated list of vulnerability types to scan. Defaults to `os,library`. */
    String pkgType

    /**
     * Name of the Nexus repository where the scan report will be stored. Defaults to `leva-documentation`. */
    String nexusReportRepository

    /**
     * Name of the Nexus repository used to proxy the location of the database of vulnerabilities located in GitHub.
     * Defaults to `docker-group-ods`. */
    String nexusDataBaseRepository

    /**
     * Name of the file that will be archived in Jenkins and uploaded in Nexus.
     * Defaults to `trivy-sbom.json`. */
    String reportFile

    /**
     * Additional flags for the Trivy CLI. Please refer to the official Trivy CLI
     * reference for possible options and don't forget to take the CLI version
     * of your ODS installation into account. The value of `additionalFlags`
     * must be a list in which the entries have the official flag name and a
     * possible value.
     * Example: `['--debug', '--timeout=10m']` */
    List<String> additionalFlags

}
