package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagCrossDocumentIndexBuildResult;
import org.aegiskb.ai.manage.support.GraphRagCrossDocumentIndex;

import java.util.List;

public interface GraphRagCrossDocumentIndexService {

    String GLOBAL_SCOPE_KEY = "global";

    List<GraphRagCrossDocumentIndexBuildResult> rebuildAll();

    default List<GraphRagCrossDocumentIndexBuildResult> rebuildAll(Long pendingDocumentId, Long pendingTaskId) {
        throw new UnsupportedOperationException("Pending D/I cross-document publication is not implemented.");
    }

    GraphRagCrossDocumentIndex loadIndex(List<Long> documentIds, List<Long> taskIds);
}
