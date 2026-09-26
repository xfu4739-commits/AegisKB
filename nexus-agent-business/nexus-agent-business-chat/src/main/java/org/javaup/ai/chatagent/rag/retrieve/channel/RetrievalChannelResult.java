package org.javaup.ai.chatagent.rag.retrieve.channel;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
public class RetrievalChannelResult {

    private String channelName;

    private List<Document> documents;

    private Map<String, Object> configSnapshot = Map.of();

    private String errorMessage = "";

    public RetrievalChannelResult(String channelName, List<Document> documents) {
        this(channelName, documents, Map.of(), "");
    }

    public RetrievalChannelResult(String channelName,
                                  List<Document> documents,
                                  Map<String, Object> configSnapshot,
                                  String errorMessage) {
        this.channelName = channelName;
        this.documents = documents;
        this.configSnapshot = configSnapshot == null ? Map.of() : Map.copyOf(configSnapshot);
        this.errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
