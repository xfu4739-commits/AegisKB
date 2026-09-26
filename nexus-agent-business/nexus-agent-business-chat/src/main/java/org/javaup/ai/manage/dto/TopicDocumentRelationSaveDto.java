package org.javaup.ai.manage.dto;

import lombok.Data;

@Data
public class TopicDocumentRelationSaveDto {

    private String knowledgeBaseId;

    private String topicId;

    private String documentId;

    private String relationScore;

    private String relationSource;

    private String reason;

    private String operatorId;
}
