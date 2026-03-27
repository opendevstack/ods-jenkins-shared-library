package org.ods.component

interface IImageRepository {

    void retagImages(String targetProject, Set<String> images,  String sourceTag, String targetTag)

}
