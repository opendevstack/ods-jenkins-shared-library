package org.ods.orchestration.util

import com.cloudbees.groovy.cps.NonCPS

class EnvironmentResolver {

    static final String DEFAULT_ENVIRONMENTS = 'dev,qa,prod'

    static final List<List<String>> ALLOWED_ENVIRONMENT_SETS = [
        ['dev', 'qa', 'prod'],
        ['dev', 'prod'],
    ]

    private final List<String> enabledEnvironments

    EnvironmentResolver(String rawEnvironments) {
        this.enabledEnvironments = resolveEnabledEnvironments(rawEnvironments)
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------
    @NonCPS
    List<String> getEnabledEnvironments() {
        return enabledEnvironments
    }

    /**
     * Returns the source environment for the given target environment.
     * The source is the previous environment in the enabled list, or 'dev' if
     * the target is first in the list or not present (e.g. 'qa' when only dev/prod are enabled).
     */
    @NonCPS
    String getSourceEnvFor(String targetEnvironment) {
        int idx = enabledEnvironments.indexOf(targetEnvironment)
        if (idx <= 0) {
            return enabledEnvironments.first()
        }
        return enabledEnvironments[idx - 1]
    }

    /**
     * Returns the Environment token for the source environment of the given target.
     * Values: D, Q, P
     */
    @NonCPS
    String getSourceEnvTokenFor(String targetEnvironment) {
        return getSourceEnvFor(targetEnvironment)[0].toUpperCase() as String
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------
    @NonCPS
    private List<String> resolveEnabledEnvironments(String raw) {
        String effective = raw?.trim() ? raw : DEFAULT_ENVIRONMENTS
        List<String> environments = effective.split(',')*.trim().findAll { it }
        validateEnvironments(environments)
        return environments
    }

    @NonCPS
    private void validateEnvironments(List<String> environments) {
        boolean valid = ALLOWED_ENVIRONMENT_SETS.any { allowed -> allowed == environments }
        if (!valid) {
            String allowedStr = ALLOWED_ENVIRONMENT_SETS*.join(',').join(' | ')
            throw new IllegalArgumentException(
                "Invalid value for ENVIRONMENTS_ENABLED: '${environments.join(',')}'. " +
                "Allowed values are: ${allowedStr}"
            )
        }
    }

}
