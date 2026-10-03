package org.aegiskb.ai.chatagent.rag.retrieve.channel;

import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionRequest;

public interface RetrievalChannel {

    String channelName();

    RetrievalChannelResult retrieve(RetrievalExecutionRequest request);
}
