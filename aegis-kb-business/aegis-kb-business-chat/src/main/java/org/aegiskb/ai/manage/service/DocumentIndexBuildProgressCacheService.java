package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocument;
import org.aegiskb.ai.manage.data.SuperAgentDocumentTask;
import org.aegiskb.ai.manage.data.SuperAgentDocumentTaskLog;
import org.aegiskb.ai.manage.vo.DocumentIndexBuildProgressVo;

/**
 * Hot progress snapshot for index-build polling. MySQL remains the source of truth.
 */
public interface DocumentIndexBuildProgressCacheService {

    DocumentIndexBuildProgressVo get(Long taskId);

    void init(SuperAgentDocument document, SuperAgentDocumentTask task);

    void update(SuperAgentDocument document, SuperAgentDocumentTask task, SuperAgentDocumentTaskLog latestLog);

    void update(SuperAgentDocument document, SuperAgentDocumentTask task);
}
