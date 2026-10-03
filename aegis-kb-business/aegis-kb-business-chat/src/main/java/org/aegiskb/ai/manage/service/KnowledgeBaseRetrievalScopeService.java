package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.KnowledgeBaseSelectionSnapshot;
import org.aegiskb.enums.ChatQueryMode;
import org.aegiskb.enums.KnowledgeBaseSelectionMode;

import java.util.Collection;

public interface KnowledgeBaseRetrievalScopeService {

    KnowledgeBaseSelectionSnapshot resolve(ChatQueryMode chatMode,
                                           KnowledgeBaseSelectionMode selectionMode,
                                           Collection<String> selectedKnowledgeBaseIds);
}
