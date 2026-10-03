package org.aegiskb.ai.chatagent.model;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aegiskb.enums.ChatQueryMode;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSessionView {

    private String conversationId;
    private boolean running;
    private int checkpointCount;
    private int messageCount;
    private String latestUserMessage;
    private String latestAssistantMessage;
    private Long latestExchangeId;
    private String latestTurnStatus;
    private String latestTurnErrorMessage;
    private ChatQueryMode chatMode;
    private String selectedDocumentId;
    private String selectedDocumentName;
    private String knowledgeBaseSelectionMode;
    private List<String> selectedKnowledgeBaseIds;
    private List<String> selectedKnowledgeBaseNames;
    private Instant createdAt;
    private Instant updatedAt;
    private List<ConversationExchangeView> exchanges;
    private ConversationMemorySummaryView memorySummary;
}
