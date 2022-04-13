package org.ods.core.test.usecase.levadoc.fixture

class DocTypeDetails {

    final String name
    final boolean needsTestData
    boolean needsComponentInfo
    boolean isOverAll

    DocTypeDetails(String name,
                   boolean needsTestData, boolean needsComponentInfo, boolean isOverAll) {
            this.name = name
            this.needsTestData = needsTestData
            this.needsComponentInfo = needsComponentInfo
            this.isOverAll = isOverAll
        }
}
