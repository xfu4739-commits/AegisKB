package org.aegiskb.ai.chatagent.rag.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aegiskb.ai.chatagent.model.memory.ConversationMemoryContext;
import org.aegiskb.ai.chatagent.rag.model.AnswerHistoryContext;
import org.aegiskb.ai.chatagent.rag.model.EvidenceAnchor;
import org.aegiskb.ai.chatagent.rag.model.HistoryPlanningContext;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationContextBundle {

    private ConversationMemoryContext memoryContext;

    private HistoryPlanningContext historyPlanningContext;

    private String historySummary;

    @Builder.Default
    private List<EvidenceAnchor> recentEvidenceAnchors = new ArrayList<>();

    private AnswerHistoryContext initialAnswerHistoryContext;
}
