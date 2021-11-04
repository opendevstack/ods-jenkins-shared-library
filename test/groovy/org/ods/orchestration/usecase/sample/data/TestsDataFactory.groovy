package org.ods.orchestration.usecase.sample.data

class TestsDataFactory {

    static List<TestDataBuilder> testDataList = [new TestDataIVRBuilder()]

    static def createData(def doctype, def projectKey) {

        def data = null
        testDataList.each {
            if (doctype.equals(it.getDocType())) {
                data = it.createData(projectKey)
            }
        }

        return data
    }
}
