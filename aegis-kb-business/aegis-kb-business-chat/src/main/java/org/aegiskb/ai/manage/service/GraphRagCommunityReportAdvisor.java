package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagCommunityReportAdvice;
import org.aegiskb.ai.manage.model.graph.GraphRagCommunityReportContext;

import java.util.Optional;

public interface GraphRagCommunityReportAdvisor {

    Optional<GraphRagCommunityReportAdvice> generateReport(GraphRagCommunityReportContext context);
}
