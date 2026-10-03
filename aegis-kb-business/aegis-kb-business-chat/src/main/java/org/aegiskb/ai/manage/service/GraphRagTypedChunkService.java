package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocumentChunk;

import java.util.List;

public interface GraphRagTypedChunkService {

    List<SuperAgentDocumentChunk> replaceTypedIndex(Long documentId,
                                                    Long taskId,
                                                    Long planId,
                                                    List<SuperAgentDocumentChunk> sourceChunks,
                                                    int startChunkNo);
}
