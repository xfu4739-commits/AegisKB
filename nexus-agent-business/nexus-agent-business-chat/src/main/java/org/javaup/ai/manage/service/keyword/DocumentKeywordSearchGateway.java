package org.javaup.ai.manage.service.keyword;

import org.javaup.ai.manage.data.SuperAgentDocumentChunk;
import org.javaup.ai.manage.model.DocumentRetrieveRequest;
import org.springframework.ai.document.Document;

import java.util.List;

public interface DocumentKeywordSearchGateway {

    void indexChunks(List<SuperAgentDocumentChunk> chunkList);

    void deleteByChunkIds(Long documentId, Long taskId, List<Long> chunkIds);

    List<Document> search(DocumentRetrieveRequest request);

    void deleteByTask(Long documentId, Long taskId);

    void deleteByDocumentId(Long documentId);
}
