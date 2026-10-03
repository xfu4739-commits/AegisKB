package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.DocumentRetrieveRequest;
import org.aegiskb.ai.manage.model.KnowledgeDocumentDescriptor;
import org.aegiskb.ai.manage.model.StructureAnchoredEvidenceRequest;
import org.springframework.ai.document.Document;

import java.util.Collection;
import java.util.List;

public interface DocumentKnowledgeService {

    List<KnowledgeDocumentDescriptor> listRetrievableDocuments();

    List<KnowledgeDocumentDescriptor> listRetrievableDocumentsByKnowledgeBaseIds(Collection<Long> knowledgeBaseIds);

    List<Document> vectorSearch(DocumentRetrieveRequest request);

    List<Document> keywordSearch(DocumentRetrieveRequest request);

    List<Document> elevateToParentBlocks(List<Document> childDocuments, int maxChars);

    default List<Document> expandStructureAnchoredEvidence(StructureAnchoredEvidenceRequest request) {
        return List.of();
    }
}
