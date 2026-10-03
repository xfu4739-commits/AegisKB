package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.support.StoredObjectInfo;
import org.aegiskb.ai.manage.support.StoredObjectMetadata;

import java.util.List;

public interface DocumentStorageService {

    StoredObjectInfo uploadOriginalFile(Long documentId, String originalFileName, byte[] bytes, String contentType);

    String uploadParsedText(Long documentId, String parsedText);

    String uploadParseArtifact(Long documentId, Long taskId, String fileName, byte[] bytes, String contentType);

    byte[] downloadObject(String objectName);

    String downloadText(String objectName);

    StoredObjectMetadata getObjectMetadata(String objectName);

    void deleteObjects(List<String> objectNameList);
}
