package org.aegiskb.ai.manage.dto;

import lombok.Data;

@Data
public class TopicDocumentRelationRemoveDto {

    private String knowledgeBaseId;

    private String topicId;

    private String documentId;

    private String operatorId;
}
