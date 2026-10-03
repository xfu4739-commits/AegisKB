package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagEvaluationReport;
import org.aegiskb.ai.manage.model.graph.GraphRagEvaluationBatchReport;
import org.aegiskb.ai.manage.model.graph.GraphRagEvaluationSuite;

import java.util.List;

public interface GraphRagEvaluationService {

    GraphRagEvaluationReport evaluate(GraphRagEvaluationSuite suite);

    GraphRagEvaluationBatchReport evaluateBatch(String batchId, String name, List<GraphRagEvaluationSuite> suites);
}
