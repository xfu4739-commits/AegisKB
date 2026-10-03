package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentRaptorNode;
import org.aegiskb.ai.manage.model.raptor.RaptorQualityReport;
import org.aegiskb.ai.ragtools.model.RagToolsRaptorBuildResponse;

import java.util.List;

public interface RaptorQualityService {

    RaptorQualityReport evaluate(Long documentId, Long taskId);

    RaptorQualityReport evaluate(List<SuperAgentRaptorNode> nodes, double configuredFloor);

    RaptorQualityReport evaluatePythonNodes(List<RagToolsRaptorBuildResponse.Node> nodes, double configuredFloor);
}
