package org.ods.service

import org.ods.Context

class GitService {

  private Context context
  private def script

  GitService(script, Context context) {
    this.script = script
    this.context = context
  }

  /** Looks in commit message for string '[ci skip]', '[ciskip]', '[ci-skip]' and '[ci_skip]'. */
  boolean isCiSkipInCommitMessage() {
    return script.sh(
        returnStdout: true, script: 'git show --pretty=%s%b -s',
        label: 'check skip CI?'
    ).toLowerCase().replaceAll("[\\s\\-\\_]", "").contains('[ciskip]')
  }
}
