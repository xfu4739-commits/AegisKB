package org.javaup.ai.manage.service;

import org.javaup.ai.manage.data.SuperAgentDocumentChunk;

import java.util.List;

public interface DocumentVectorGateway {

    void vectorize(List<SuperAgentDocumentChunk> chunkList);

    void deleteByChunkIds(Long documentId, Long taskId, List<Long> chunkIds);

    void deleteByTask(Long documentId, Long taskId);

    void deleteByDocumentId(Long documentId);
}
