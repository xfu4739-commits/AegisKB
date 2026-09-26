package org.javaup.ai.manage.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocumentDescriptor {

    private Long documentId;

    private String documentName;

    private Long lastIndexTaskId;

    private Long knowledgeBaseId;

    private String knowledgeBaseName;
}
