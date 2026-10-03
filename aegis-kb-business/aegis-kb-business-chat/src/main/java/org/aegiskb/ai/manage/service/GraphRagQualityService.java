package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagQualityReport;

public interface GraphRagQualityService {

    GraphRagQualityReport evaluate(Long documentId, Long taskId);
}
