package org.javaup.ai.chatagent.rag.service;

import org.javaup.ai.chatagent.rag.model.RagRuntimeOptions;
import org.javaup.ai.manage.model.KnowledgeBaseSelectionSnapshot;
import org.javaup.ai.manage.model.KnowledgeDocumentDescriptor;
import org.javaup.enums.KnowledgeBaseSelectionMode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeBoundaryResolver {

    public RagRuntimeOptions runtimeOptions(KnowledgeBaseSelectionSnapshot knowledgeBaseSelection) {
        return knowledgeBaseSelection == null || knowledgeBaseSelection.getRagRuntimeOptions() == null
            ? RagRuntimeOptions.defaults()
            : knowledgeBaseSelection.getRagRuntimeOptions();
    }

    public KnowledgeBaseSelectionMode selectionMode(KnowledgeBaseSelectionSnapshot knowledgeBaseSelection) {
        return knowledgeBaseSelection == null || knowledgeBaseSelection.getSelectionMode() == null
            ? KnowledgeBaseSelectionMode.NONE
            : knowledgeBaseSelection.getSelectionMode();
    }

    public List<Long> selectedKnowledgeBaseIds(KnowledgeBaseSelectionSnapshot knowledgeBaseSelection) {
        return knowledgeBaseSelection == null || knowledgeBaseSelection.getSelectedKnowledgeBaseIds() == null
            ? List.of()
            : knowledgeBaseSelection.getSelectedKnowledgeBaseIds();
    }

    public List<String> selectedKnowledgeBaseNames(KnowledgeBaseSelectionSnapshot knowledgeBaseSelection) {
        return knowledgeBaseSelection == null || knowledgeBaseSelection.getSelectedKnowledgeBaseNames() == null
            ? List.of()
            : knowledgeBaseSelection.getSelectedKnowledgeBaseNames();
    }

    public List<KnowledgeDocumentDescriptor> allowedDocuments(KnowledgeBaseSelectionSnapshot knowledgeBaseSelection) {
        return knowledgeBaseSelection == null || knowledgeBaseSelection.getAllowedDocuments() == null
            ? List.of()
            : knowledgeBaseSelection.getAllowedDocuments();
    }

    public List<Long> allowedDocumentIds(KnowledgeBaseSelectionSnapshot knowledgeBaseSelection) {
        return knowledgeBaseSelection == null || knowledgeBaseSelection.getAllowedDocumentIds() == null
            ? List.of()
            : knowledgeBaseSelection.getAllowedDocumentIds();
    }

    public List<Long> allowedTaskIds(KnowledgeBaseSelectionSnapshot knowledgeBaseSelection) {
        return knowledgeBaseSelection == null || knowledgeBaseSelection.getAllowedTaskIds() == null
            ? List.of()
            : knowledgeBaseSelection.getAllowedTaskIds();
    }
}
