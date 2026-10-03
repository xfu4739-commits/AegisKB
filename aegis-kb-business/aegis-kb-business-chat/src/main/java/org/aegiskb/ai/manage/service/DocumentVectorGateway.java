package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocumentChunk;

import java.util.List;

public interface DocumentVectorGateway {

    void vectorize(List<SuperAgentDocumentChunk> chunkList);

    void deleteByChunkIds(Long documentId, Long taskId, List<Long> chunkIds);

    void deleteByTask(Long documentId, Long taskId);

    void deleteByDocumentId(Long documentId);
}
