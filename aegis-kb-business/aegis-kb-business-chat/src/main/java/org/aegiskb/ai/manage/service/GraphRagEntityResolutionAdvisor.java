package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagEntityResolutionAdvice;
import org.aegiskb.ai.manage.model.graph.GraphRagEntityResolutionContext;

import java.util.Optional;

public interface GraphRagEntityResolutionAdvisor {

    Optional<GraphRagEntityResolutionAdvice> advise(GraphRagEntityResolutionContext context);
}
