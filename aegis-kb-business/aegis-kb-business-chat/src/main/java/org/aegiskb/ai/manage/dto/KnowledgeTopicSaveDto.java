package org.aegiskb.ai.manage.dto;

import lombok.Data;

@Data
public class KnowledgeTopicSaveDto {

    private String id;

    private String knowledgeBaseId;

    private String topicName;

    private String scopeId;

    private String description;

    private String aliases;

    private String examples;

    private String answerShape;

    private String executionPreference;

    private String sortOrder;

    private String operatorId;
}
