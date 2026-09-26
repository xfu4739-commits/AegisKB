package org.javaup.ai.chatagent.rag.retrieve.channel;

import org.javaup.ai.chatagent.rag.model.RetrievalExecutionRequest;

public interface RetrievalChannel {

    String channelName();

    RetrievalChannelResult retrieve(RetrievalExecutionRequest request);
}
