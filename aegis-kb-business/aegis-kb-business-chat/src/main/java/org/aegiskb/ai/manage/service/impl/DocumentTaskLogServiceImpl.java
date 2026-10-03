package org.aegiskb.ai.manage.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.aegiskb.ai.manage.data.SuperAgentDocumentTaskLog;
import org.aegiskb.ai.manage.mapper.SuperAgentDocumentTaskLogMapper;
import org.aegiskb.ai.manage.service.DocumentTaskLogService;
import org.aegiskb.enums.BusinessStatus;
import org.springframework.stereotype.Service;

@AllArgsConstructor
@Service
public class DocumentTaskLogServiceImpl implements DocumentTaskLogService {

    private final SuperAgentDocumentTaskLogMapper taskLogMapper;
    private final ObjectMapper objectMapper;
    private final UidGenerator uidGenerator;

    @Override
    public SuperAgentDocumentTaskLog saveLog(Long taskId,
                                             Long documentId,
                                             Integer stageType,
                                             Integer eventType,
                                             Integer logLevel,
                                             Integer operatorType,
                                             Long operatorId,
                                             String content,
                                             Object detail) {
        SuperAgentDocumentTaskLog log = new SuperAgentDocumentTaskLog();
        log.setId(uidGenerator.getUid());
        log.setTaskId(taskId);
        log.setDocumentId(documentId);
        log.setStageType(stageType);
        log.setEventType(eventType);
        log.setLogLevel(logLevel);
        log.setOperatorType(operatorType);
        log.setOperatorId(operatorId);
        log.setContent(content);
        log.setDetailJson(toJson(detail));
        log.setStatus(BusinessStatus.YES.getCode());
        taskLogMapper.insert(log);
        return log;
    }

    // 把扩展字段序列化为 JSON 字符串
    private String toJson(Object detail) {
        if (detail == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(detail);
        }
        catch (JsonProcessingException exception) {
            // Map集合实现了toString方法
            return String.valueOf(detail);
        }
    }
}
