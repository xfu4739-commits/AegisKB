package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocumentChunk;
import org.aegiskb.ai.manage.model.graph.GraphRagBuildResult;

import java.util.List;

public interface GraphRagBuildService {

    GraphRagBuildResult rebuildDocumentGraph(Long documentId, Long taskId, List<SuperAgentDocumentChunk> chunks);

    void deleteByTask(Long documentId, Long taskId);

    void deleteByDocumentId(Long documentId);
}
