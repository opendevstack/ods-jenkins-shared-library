package org.ods.orchestration.usecase.sample.data

abstract class TestDataBuilder {

    abstract def getDocType();

    abstract def createData(def projectKey);
}
