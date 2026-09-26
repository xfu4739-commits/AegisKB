package org.javaup.ai.chatagent.rag.retrieve.channel;

import org.javaup.ai.chatagent.rag.model.RetrievalExecutionRequest;
import org.javaup.ai.chatagent.rag.service.DocumentRetrieveRequestFactory;
import org.javaup.ai.manage.service.DocumentKnowledgeService;
import org.javaup.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.javaup.enums.RetrievalChannelEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class VectorRetrievalChannel implements RetrievalChannel {

    private final DocumentKnowledgeService documentKnowledgeService;
    private final DocumentRetrieveRequestFactory documentRetrieveRequestFactory;

    public VectorRetrievalChannel(DocumentKnowledgeService documentKnowledgeService,
                                  DocumentRetrieveRequestFactory documentRetrieveRequestFactory) {
        this.documentKnowledgeService = documentKnowledgeService;
        this.documentRetrieveRequestFactory = documentRetrieveRequestFactory;
    }

    @Override
    public String channelName() {
        return RetrievalChannelEnum.VECTOR.getName();
    }

    @Override
    public RetrievalChannelResult retrieve(RetrievalExecutionRequest request) {

        List<Document> documentList = documentKnowledgeService.vectorSearch(
            documentRetrieveRequestFactory.build(request, channelName())
        );
        double minimumScore = request.requireChannel(channelName()).minimumScore();
        List<Document> accepted = documentList == null ? List.of() : documentList.stream()
            .filter(document -> {
                Double score = resolveScore(document);
                return score != null && score >= minimumScore;
            })
            .toList();
        return new RetrievalChannelResult(
            channelName(), accepted
        );
    }

    private Double resolveScore(Document document) {
        if (document == null) {
            return null;
        }
        Object metadataScore = document.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
        return metadataScore instanceof Number number ? number.doubleValue() : document.getScore();
    }
}
