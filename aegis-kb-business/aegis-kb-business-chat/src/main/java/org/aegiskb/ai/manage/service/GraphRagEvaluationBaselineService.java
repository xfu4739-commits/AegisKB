package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagEvaluationBatchReport;

public interface GraphRagEvaluationBaselineService {

    GraphRagEvaluationBatchReport evaluateO6LlmNerBaseline();

    GraphRagEvaluationBatchReport evaluateO6CrossDocumentBaseline();
}
