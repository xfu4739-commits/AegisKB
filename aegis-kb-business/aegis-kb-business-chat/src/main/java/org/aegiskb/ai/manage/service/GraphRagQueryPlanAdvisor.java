package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagQueryCatalog;
import org.aegiskb.ai.manage.model.graph.GraphRagQueryPlanAdvice;

import java.util.Optional;

public interface GraphRagQueryPlanAdvisor {

    Optional<GraphRagQueryPlanAdvice> advise(String question, GraphRagQueryCatalog catalog);
}
