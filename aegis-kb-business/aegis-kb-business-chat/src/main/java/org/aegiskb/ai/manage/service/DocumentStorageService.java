package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.support.StoredObjectInfo;
import org.aegiskb.ai.manage.support.StoredObjectMetadata;

import java.io.InputStream;
import java.util.List;

public interface DocumentStorageService {

    /**
     * 按调用方给出的长度把原文件流写入对象存储。调用方负责关闭流；实现不得先把流读成完整 byte[]。
     */
    StoredObjectInfo uploadOriginalFile(Long documentId, String originalFileName, InputStream inputStream, long objectSize, String contentType);

    String uploadParsedText(Long documentId, String parsedText);

    String uploadParseArtifact(Long documentId, Long taskId, String fileName, byte[] bytes, String contentType);

    byte[] downloadObject(String objectName);

    String downloadText(String objectName);

    StoredObjectMetadata getObjectMetadata(String objectName);

    void deleteObjects(List<String> objectNameList);
}
