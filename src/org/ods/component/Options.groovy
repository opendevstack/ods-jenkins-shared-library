package org.ods.component

import groovy.transform.TypeChecked

// Options shared by all stages.
@TypeChecked
class Options {

    /**
     * Branches to run stage for.
     * Example: `['master', 'develop']`.
     * Next to exact matches, it also supports prefixes (e.g. `feature/`) and all branches (`*`). */
    List<String> branches

    /**
     * Branch to run stage for.
     * Example: `'master'`.
     * Next to exact matches, it also supports prefixes (e.g. `feature/`) and all branches (`*`). */
    String branch

}
