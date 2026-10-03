package org.aegiskb.ai.chatagent.rag.retrieve.channel;

import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionRequest;
import org.aegiskb.ai.chatagent.rag.service.DocumentRetrieveRequestFactory;
import org.aegiskb.ai.manage.service.DocumentKnowledgeService;
import org.aegiskb.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.aegiskb.enums.RetrievalChannelEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class KeywordRetrievalChannel implements RetrievalChannel {

    private final DocumentKnowledgeService documentKnowledgeService;
    private final DocumentRetrieveRequestFactory documentRetrieveRequestFactory;

    public KeywordRetrievalChannel(DocumentKnowledgeService documentKnowledgeService,
                                   DocumentRetrieveRequestFactory documentRetrieveRequestFactory) {
        this.documentKnowledgeService = documentKnowledgeService;
        this.documentRetrieveRequestFactory = documentRetrieveRequestFactory;
    }

    @Override
    public String channelName() {
        return RetrievalChannelEnum.KEYWORD.getName();
    }

    @Override
    public RetrievalChannelResult retrieve(RetrievalExecutionRequest request) {
        List<Document> documentList = documentKnowledgeService.keywordSearch(
            documentRetrieveRequestFactory.build(request, channelName())
        );
        List<Document> candidates = documentList == null ? List.of() : documentList;
        Double topScore = candidates.stream()
            .map(this::resolveScore)
            .filter(java.util.Objects::nonNull)
            .max(Double::compareTo)
            .orElse(null);
        if (topScore == null || topScore <= 0D) {
            return new RetrievalChannelResult(channelName(), candidates);
        }
        double acceptedFloor = topScore * Math.max(0D,
            request.requireChannel(channelName()).relativeScoreFloor());
        List<Document> accepted = candidates.stream()
            .filter(document -> {
                Double score = resolveScore(document);
                return score != null && score >= acceptedFloor;
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
