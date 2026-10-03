package org.aegiskb.ai.chatagent.service;

import org.aegiskb.ai.chatagent.model.ConversationExchangeView;
import org.aegiskb.ai.chatagent.model.SearchReference;
import org.aegiskb.ai.chatagent.model.debug.ChatDebugTrace;
import org.aegiskb.ai.manage.model.KnowledgeBaseSelectionSnapshot;
import org.aegiskb.enums.ChatQueryMode;
import org.aegiskb.enums.ChatTurnStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ConversationArchiveStore {

    ConversationExchangeView startExchange(String conversationId,
                                           String question,
                                           ChatQueryMode chatMode,
                                           Long selectedDocumentId,
                                           String selectedDocumentName,
                                           KnowledgeBaseSelectionSnapshot knowledgeBaseSelection);

    void refreshSessionScope(String conversationId,
                             ChatQueryMode chatMode,
                             Long selectedDocumentId,
                             String selectedDocumentName,
                             KnowledgeBaseSelectionSnapshot knowledgeBaseSelection);

    void completeExchange(String conversationId,
                          long exchangeId,
                          String answer,
                          List<String> thinkingSteps,
                          List<SearchReference> references,
                          List<String> recommendations,
                          List<String> usedTools,
                          ChatDebugTrace debugTrace,
                          ChatTurnStatus status,
                          String errorMessage,
                          Long firstResponseTimeMs,
                          Long totalResponseTimeMs);

    Optional<ConversationArchiveRecord> getSessionRecord(String conversationId);

    List<ConversationExchangeView> listExchanges(String conversationId);

    List<ConversationExchangeView> listExchangesAfter(String conversationId, long afterExchangeId);

    List<ConversationExchangeView> listRecentExchanges(String conversationId, int limit);

    List<ConversationArchiveRecord> listSessionRecords();

    ConversationArchivePage listSessionRecordPage(int pageNo,
                                                  int pageSize,
                                                  String keyword,
                                                  ChatQueryMode chatMode,
                                                  ChatTurnStatus latestTurnStatus);

    ConversationRemovalResult deleteSession(String conversationId);

    record ConversationArchiveRecord(
        String conversationId,
        boolean running,
        ChatQueryMode chatMode,
        Long selectedDocumentId,
        String selectedDocumentName,
        String knowledgeBaseSelectionMode,
        List<String> selectedKnowledgeBaseIds,
        List<String> selectedKnowledgeBaseNames,
        Instant createdAt,
        Instant updatedAt,
        List<ConversationExchangeView> exchanges
    ) {
    }

    record ConversationRemovalResult(
        int removedDialogueCount,
        int removedExchangeCount
    ) {
    }

    record ConversationArchivePage(
        long pageNo,
        long pageSize,
        long totalSize,
        List<ConversationArchiveRecord> records
    ) {
    }
}
