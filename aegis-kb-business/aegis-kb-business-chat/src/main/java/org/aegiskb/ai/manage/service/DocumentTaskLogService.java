package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocumentTaskLog;

public interface DocumentTaskLogService {

    SuperAgentDocumentTaskLog saveLog(Long taskId,
                                      Long documentId,
                                      Integer stageType,
                                      Integer eventType,
                                      Integer logLevel,
                                      Integer operatorType,
                                      Long operatorId,
                                      String content,
                                      Object detail);
}
