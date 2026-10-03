package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagExtractionAdvice;
import org.aegiskb.ai.manage.model.graph.GraphRagExtractionContext;

import java.util.Optional;

public interface GraphRagExtractionAdvisor {

    Optional<GraphRagExtractionAdvice> extract(GraphRagExtractionContext context);
}
