package org.aegiskb.ai.chatagent.rag.fixture;

import org.aegiskb.ai.chatagent.rag.model.GraphIntent;
import org.aegiskb.ai.chatagent.rag.model.RankFeatureBundle;
import org.aegiskb.ai.chatagent.rag.model.RaptorIntent;
import org.aegiskb.ai.chatagent.rag.model.RetrievalChannelPlan;
import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionQuery;
import org.aegiskb.ai.chatagent.rag.model.RetrievalMetadataFilters;
import org.aegiskb.ai.chatagent.rag.model.RetrievalPlan;
import org.aegiskb.ai.chatagent.rag.model.RetrievalQuestionPlan;
import org.aegiskb.ai.chatagent.rag.model.RetrievalRoutePlan;
import org.aegiskb.ai.chatagent.rag.model.RouteScopeAuthorizationMode;
import org.aegiskb.ai.chatagent.rag.model.TableIntent;
import org.aegiskb.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.aegiskb.enums.ChatQueryMode;
import org.aegiskb.enums.KnowledgeBaseSelectionMode;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * S19 共享中性 fixture：最小合法 RetrievalPlan 与 Source 候选文档。
 *
 * <p>所有名称均为中性 fixture 值，不包含业务词、文档名或样例语义。</p>
 */
public final class RetrievalPlanFixtures {

    public static final long KNOWLEDGE_BASE_ID = 9001L;
    public static final long DOCUMENT_ID = 8001L;
    public static final long TASK_ID = 7001L;

    private RetrievalPlanFixtures() {
    }

    public static RetrievalExecutionQuery executionQuery(int index, String executionQuery) {
        return RetrievalExecutionQuery.builder()
            .index(index)
            .sourceQuestion("fixture question")
            .normalizedQuery("fixture normalized query")
            .executionQuery(executionQuery)
            .build();
    }

    public static RetrievalChannelPlan channelPlan(String channelName) {
        return RetrievalChannelPlan.builder()
            .channelName(channelName)
            .enabled(true)
            .topK(10)
            .timeoutMs(2000L)
            .budget(5)
            .weight(1.0D)
            .minimumScore(0D)
            .relativeScoreFloor(0D)
            .build();
    }

    public static RetrievalChannelPlan disabledChannelPlan(String channelName) {
        RetrievalChannelPlan plan = channelPlan(channelName);
        plan.setEnabled(false);
        return plan;
    }

    public static RetrievalPlan plan(List<RetrievalExecutionQuery> executionQueries,
                                     List<RetrievalChannelPlan> channels) {
        return RetrievalPlan.builder()
            .questionPlan(RetrievalQuestionPlan.builder()
                .currentQuestion("fixture question")
                .rewrittenQuestion("fixture question")
                .normalizedQuery("fixture normalized query")
                .executionQueries(new ArrayList<>(executionQueries))
                .build())
            .chatMode(ChatQueryMode.DOCUMENT)
            .scopeMode(KnowledgeBaseSelectionMode.SELECTED)
            .knowledgeBaseIds(List.of(KNOWLEDGE_BASE_ID))
            .allowedDocumentScope(List.of(DOCUMENT_ID))
            .documentScope(List.of(DOCUMENT_ID))
            .taskScope(List.of(TASK_ID))
            .metadataFilters(RetrievalMetadataFilters.builder().build())
            .channels(new ArrayList<>(channels))
            .tableIntent(TableIntent.builder().source("FIXTURE").build())
            .graphIntent(GraphIntent.builder().source("FIXTURE").build())
            .raptorIntent(RaptorIntent.builder().source("FIXTURE").build())
            .routePlan(RetrievalRoutePlan.builder()
                .source("FIXTURE")
                .status("OK")
                .confidence(1.0D)
                .authorizationMode(RouteScopeAuthorizationMode.EXPLICIT_DOCUMENT)
                .scopeAuthorizationReason("FIXTURE_AUTHORIZATION")
                .authorizedDocumentIds(List.of(DOCUMENT_ID))
                .authorizedTaskIds(List.of(TASK_ID))
                .topDocumentHintId(DOCUMENT_ID)
                .topTaskHintId(TASK_ID)
                .build())
            .rankFeatures(RankFeatureBundle.builder()
                .rankWeight(1.0D)
                .originalScoreWeight(0.08D)
                .metadataBoostWeight(0.04D)
                .maxMetadataBoost(1.0D)
                .build())
            .candidateWindow(40)
            .rerankWindow(24)
            .rerankRequested(false)
            .finalEvidenceBudget(6)
            .subQuestionTimeoutMs(5000L)
            .source("FIXTURE")
            .build();
    }

    public static RetrievalPlan singleQueryPlan(String channelName) {
        return plan(List.of(executionQuery(1, "fixture execution query")), List.of(channelPlan(channelName)));
    }

    public static Document sourceDocument(String id, long chunkId, double score) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_ID, DOCUMENT_ID);
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_ID, chunkId);
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, score);
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL, "FIXTURE");
        metadata.put(DocumentKnowledgeMetadataKeys.SOURCE_TYPE, "CHUNK");
        metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME, "fixture-document");
        metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_BASE_ID, KNOWLEDGE_BASE_ID);
        metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_BASE_NAME, "fixture-kb");
        return Document.builder()
            .id(id)
            .text("fixture evidence text " + id)
            .metadata(metadata)
            .score(score)
            .build();
    }
}
