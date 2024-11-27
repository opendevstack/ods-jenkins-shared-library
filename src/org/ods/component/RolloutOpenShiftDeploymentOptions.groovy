package org.ods.component

import groovy.transform.TypeChecked

@TypeChecked
class RolloutOpenShiftDeploymentOptions extends Options {

    /**
     * Selector scope used to determine which resources are part of a component
     * (defaults to `context.selector`). */
    String selector

    /**
     * Image tag on which to apply the `latest` tag (defaults to `context.shortGitCommit`). */
    String imageTag

    /**
     * Adjust timeout of rollout (defaults to 5 minutes). Caution: This needs to
     * be aligned with the deployment strategy timeout (timeoutSeconds) and the
     * readiness probe timeouts (initialDelaySeconds + failureThreshold * periodSeconds). */
    Integer deployTimeoutMinutes

    /**
     * Adjust retries to wait for the pod during a rollout (defaults to 5). */
    Integer deployTimeoutRetries

    /**
     * Directory of Helm chart (defaults to `chart`). */
    String chartDir

    /**
     * Name of the Helm release (defaults to `context.componentId`). Change this
     * value if you want to install separate instances of the Helm chart in the
     * same namespace. In that case, make sure to use `{{ .Release.Name }}` in
     * resource names to avoid conflicts.  Only relevant if the directory
     * referenced by `chartDir` exists. */
    String helmReleaseName

    /**
     * Key/value pairs to pass as values. Only relevant if the directory
     * referenced by `chartDir` exists.
     *
     * See also `helm --set`. Be aware that the resulting data type may vary depending on the input.
     */
    Map<String, String> helmValues

    /**
     * Key/value pairs to pass as values (by default, the key `imageTag` is set
     * to the config option `imageTag`). Only relevant if the directory
     * referenced by `chartDir` exists.
     *
     * See also `helm --set-string`.
     */
    Map<String, String> helmStringValues

    /**
     * List of paths to values files (empty by default). Only relevant if the
     * directory referenced by `chartDir` exists. */
    List<String> helmValuesFiles

    /**
     * List of paths to values files (empty by default). Only relevant if the
     * directory referenced by `chartDir` exists.
     * These must contain a suffix called '.env.yml' - which will be replaced
     * during rollout and deployment, and then added to helmValueFiles
     *
     * Passing a string literal of 'values.env.yaml' will be expanded to their respective environments.
     *
     * For example: 'values.env.yaml' will become 'values.dev.yaml', 'values.test.yaml' or 'values.prod.yaml'.
     * That means creating the usual files that are named after their respective environment are parsed as usual.
     */
    List<String> helmEnvBasedValuesFiles

    /**
     * List of default flags to be passed verbatim to to `helm upgrade`
     * (defaults to `['--install', '--atomic']`). Typically these should not be
     * modified - if you want to pass more flags, use `helmAdditionalFlags`
     * instead. Only relevant if the directory referenced by `chartDir` exists. */
    List<String> helmDefaultFlags

    /**
     * List of additional flags to be passed verbatim to to `helm upgrade`
     *(empty by default). Only relevant if the directory referenced by
     *`chartDir` exists. */
    List<String> helmAdditionalFlags

    /**
     * Whether to show diff explaining changes to the release before running
     * `helm upgrade` (`true` by default). Only relevant if the directory
     * referenced by `chartDir` exists. */
    boolean helmDiff

    /**
     * Credentials name of the private key used by helm-secrets (defaults to
     * `${context.cdProject}-helm-private-key`). The fingerprint must match the
     * one specified in `.sops.yaml`. Only relevant if the directory referenced
     * by `chartDir` exists. */
    String helmPrivateKeyCredentialsId

    /**
     * Directory with OpenShift templates (defaults to `openshift`). */
    String openshiftDir

    /**
     * Credentials name of the private key used by Tailor (defaults to
     * `${context.cdProject}-tailor-private-key`). Only relevant if the
     * directory referenced by `openshiftDir` exists. */
    String tailorPrivateKeyCredentialsId

    /**
     * Selector scope used by Tailor (defaults to config option `selector`).
     * Only relevant if the directory referenced by `openshiftDir` exists. */
    String tailorSelector

    /**
     * Whether Tailor verifies the live configuration against the desired state
     * after application (defaults to `true`). Only relevant if the directory
     * referenced by `openshiftDir` exists. */
    boolean tailorVerify

    /**
     * Resource kind exclusion used by Tailor (defaults to `bc,is`). Only
     * relevant if the directory referenced by `openshiftDir` exists. */
    String tailorExclude

    /**
     * Path to Tailor parameter file (defaults to none). Only relevant if the
     * directory referenced by `openshiftDir` exists. */
    String tailorParamFile

    /**
     * Paths to preserve in the live configuration (defaults to `[]`). Only
     * relevant if the directory referenced by `openshiftDir` exists. */
    List<String> tailorPreserve

    /**
     * Additional parameters to pass to Tailor (defaults to `[]`). Only
     * relevant if the directory referenced by `openshiftDir` exists. */
    List<String> tailorParams

}
