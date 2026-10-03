package org.aegiskb.ai.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeScopeItemVo {

    private String id;

    private String knowledgeBaseId;

    private String scopeName;

    private String parentScopeId;

    private String description;

    private String aliases;

    private String examples;

    private String sortOrder;
}
