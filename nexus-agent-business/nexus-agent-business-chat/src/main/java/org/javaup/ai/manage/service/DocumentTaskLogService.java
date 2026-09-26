package org.javaup.ai.manage.service;

import org.javaup.ai.manage.data.SuperAgentDocumentTaskLog;

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
