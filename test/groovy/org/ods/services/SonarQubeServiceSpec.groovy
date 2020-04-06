package org.ods.services

import org.ods.services.SonarQubeService
import spock.lang.*

class SonarQubeServiceSpec extends Specification {

  @Unroll
  def "enabled for branch"() {
    expect:
    new SonarQubeService(null, 'foo').enabledForBranch(
      gitBranch,
      configuredBranch,
      serverVersion,
      serverEdition
    ) == enabled

    where:
    gitBranch      | configuredBranch | serverVersion | serverEdition || enabled
    'master'       | 'master'         | '7.9'         | 'community'   || true
    'develop'      | '*'              | '7.9'         | 'community'   || true
    'develop'      | 'master'         | '7.9'         | 'community'   || false
    'release/1.0.0'| 'release/'       | '7.9'         | 'community'   || true
    'feature/foo'  | 'release/'       | '7.9'         | 'community'   || false
    'feature/foo'  | 'master'         | '7.9'         | 'developer'   || true
    'feature/foo'  | 'master'         | '7.9'         | 'enterprise'  || true
    'feature/foo'  | 'master'         | '7.9'         | 'datacenter'  || true
  }
}
