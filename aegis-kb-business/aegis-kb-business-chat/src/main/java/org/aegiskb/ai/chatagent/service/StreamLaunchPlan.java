package org.aegiskb.ai.chatagent.service;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.aegiskb.ai.manage.model.KnowledgeBaseSelectionSnapshot;
import org.aegiskb.enums.ChatQueryMode;

import java.time.LocalDate;

@Data
@AllArgsConstructor
public class StreamLaunchPlan {

    private final String question;

    private final String conversationId;

    private final ChatQueryMode chatMode;

    private final Long selectedDocumentId;

    private final String selectedDocumentName;

    private final Long selectedTaskId;

    private final KnowledgeBaseSelectionSnapshot knowledgeBaseSelectionSnapshot;

    private final String leaseKey;

    private final String leaseOwnerToken;

    private final LocalDate currentDate;

    private final String currentDateText;
}
