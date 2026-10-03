package org.aegiskb.ai.manage.dto;

import lombok.Data;

@Data
public class KnowledgeScopeSaveDto {

    private String id;

    private String knowledgeBaseId;

    private String scopeName;

    private String parentScopeId;

    private String description;

    private String aliases;

    private String examples;

    private String sortOrder;

    private String operatorId;
}
