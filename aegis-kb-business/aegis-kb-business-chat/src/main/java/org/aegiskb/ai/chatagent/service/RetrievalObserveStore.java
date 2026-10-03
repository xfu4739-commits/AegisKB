package org.aegiskb.ai.chatagent.service;

import org.aegiskb.ai.chatagent.model.ChannelExecutionView;
import org.aegiskb.ai.chatagent.model.RetrievalResultView;

import java.util.List;

public interface RetrievalObserveStore {

    int batchSaveResults(String conversationId, long exchangeId, List<RetrievalResultView> results);

    void batchSaveChannelExecutions(String conversationId, long exchangeId, List<ChannelExecutionView> executions);

    List<RetrievalResultView> listResults(String conversationId, long exchangeId);

    List<ChannelExecutionView> listChannelExecutions(String conversationId, long exchangeId);

    void deleteByConversation(String conversationId);
}
