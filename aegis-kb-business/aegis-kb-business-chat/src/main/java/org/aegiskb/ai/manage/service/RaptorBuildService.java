package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocumentChunk;
import org.aegiskb.ai.manage.model.raptor.RaptorBuildResult;

import java.util.List;

public interface RaptorBuildService {

    RaptorBuildResult rebuildDocumentTree(Long documentId, Long taskId, List<SuperAgentDocumentChunk> chunks);

    RaptorBuildResult rebuildKnowledgeScopeTree(Long knowledgeBaseId, Long scopeId);

    void deleteByTask(Long documentId, Long taskId);

    void deleteByDocumentId(Long documentId);

    void deleteByScope(String scopeType, String scopeKey);
}
