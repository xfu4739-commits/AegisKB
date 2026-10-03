package org.aegiskb.ai.chatagent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aegiskb.ai.chatagent.model.trace.ConversationTraceStageView;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationExchangeDetailView {

    private String conversationId;

    private ConversationExchangeView exchange;

    private List<ConversationTraceStageView> stageTraces;
}
