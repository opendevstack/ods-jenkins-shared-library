package org.ods.core.test.usecase.levadoc.fixture

class DocTypeDetails {

    final String name
    final boolean needsTestData
    boolean needsComponents
    boolean isOverAll
    boolean needsAllComponents

    DocTypeDetails(String name,
            boolean needsTestData, boolean needsComponents, boolean isOverAll, boolean needsAllComponents) {
            this.name = name
            this.needsTestData = needsTestData
            this.needsComponents = needsComponents
            this.isOverAll = isOverAll
            this.needsAllComponents = needsAllComponents
        }
}
