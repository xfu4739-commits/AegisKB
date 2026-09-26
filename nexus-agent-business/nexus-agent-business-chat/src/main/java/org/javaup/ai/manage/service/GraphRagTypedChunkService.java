package org.javaup.ai.manage.service;

import org.javaup.ai.manage.data.SuperAgentDocumentChunk;

import java.util.List;

public interface GraphRagTypedChunkService {

    List<SuperAgentDocumentChunk> replaceTypedIndex(Long documentId,
                                                    Long taskId,
                                                    Long planId,
                                                    List<SuperAgentDocumentChunk> sourceChunks,
                                                    int startChunkNo);
}
