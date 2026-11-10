package org.aegiskb.ai.chatagent.rag.service;

import org.aegiskb.ai.chatagent.rag.model.DocumentNavigationDecision;
import org.aegiskb.ai.chatagent.rag.model.ExecutionMode;
import org.aegiskb.ai.chatagent.rag.model.QueryType;
import org.aegiskb.ai.chatagent.rag.model.QueryUnderstandingResult;
import org.aegiskb.ai.manage.service.DocumentNavigationIndexService;
import org.aegiskb.ai.manage.service.DocumentStructureGraphService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S19-P5 目标不变量：SAG 模式决策只由受控 LLM advisor 的 queryType + 置信度驱动。
 *
 * <p>高置信 {@link QueryType#MULTI_HOP_RESEARCH} 翻译为 {@link ExecutionMode#SAG}；普通文档问答保持
 * {@link ExecutionMode#RETRIEVAL}；置信度不足时 fail closed 回 {@link ExecutionMode#RETRIEVAL}。
 * 判定不读取任何业务词、文档名或 contains 规则。</p>
 */
class DocumentQuestionRouterSagTest {

    @Test
    void highConfidenceMultiHopResearchRoutesToSag() {
        DocumentQuestionRouter router = routerWithUnderstanding(
            QueryUnderstandingResult.builder()
                .queryType(QueryType.MULTI_HOP_RESEARCH)
                .confidence(0.9D)
                .source("llm-query-understanding")
                .reasons(new ArrayList<>())
                .build()
        );

        DocumentNavigationDecision decision = router.route(
            8001L,
            "把内部上线流程与当前外部监管要求逐项对照后给出差异",
            null,
            "",
            ""
        );

        assertThat(decision.getExecutionMode()).isEqualTo(ExecutionMode.SAG);
        assertThat(decision.getSummaryText()).contains("mode=SAG");
    }

    @Test
    void ordinaryDocumentQuestionStaysRetrieval() {
        DocumentQuestionRouter router = routerWithUnderstanding(
            QueryUnderstandingResult.builder()
                .queryType(QueryType.DOCUMENT_QA)
                .confidence(0.9D)
                .source("llm-query-understanding")
                .reasons(new ArrayList<>())
                .build()
        );

        DocumentNavigationDecision decision = router.route(
            8001L,
            "这份文档正文里描述的核心结论是什么",
            null,
            "",
            ""
        );

        assertThat(decision.getExecutionMode()).isEqualTo(ExecutionMode.RETRIEVAL);
        assertThat(decision.getSummaryText()).contains("mode=RETRIEVAL");
    }

    @Test
    void lowConfidenceMultiHopResearchFailsClosedToRetrieval() {
        DocumentQuestionRouter router = routerWithUnderstanding(
            QueryUnderstandingResult.builder()
                .queryType(QueryType.MULTI_HOP_RESEARCH)
                .confidence(0.5D)
                .source("llm-query-understanding")
                .reasons(new ArrayList<>())
                .build()
        );

        DocumentNavigationDecision decision = router.route(
            8001L,
            "把内部上线流程与当前外部监管要求逐项对照后给出差异",
            null,
            "",
            ""
        );

        assertThat(decision.getExecutionMode()).isEqualTo(ExecutionMode.RETRIEVAL);
        assertThat(decision.getSummaryText()).contains("mode=RETRIEVAL");
    }

    @SuppressWarnings("unchecked")
    private DocumentQuestionRouter routerWithUnderstanding(QueryUnderstandingResult understanding) {
        QueryUnderstandingService queryUnderstandingService = mock(QueryUnderstandingService.class);
        when(queryUnderstandingService.understand(anyString(), anyString(), anyList(), anyString(), anyString()))
            .thenReturn(understanding);
        ObjectProvider<QueryUnderstandingService> queryUnderstandingProvider = mock(ObjectProvider.class);
        when(queryUnderstandingProvider.getIfAvailable()).thenReturn(queryUnderstandingService);
        ObjectProvider<DocumentNavigationIndexService> navigationIndexProvider = mock(ObjectProvider.class);
        when(navigationIndexProvider.getIfAvailable()).thenReturn(null);
        return new DocumentQuestionRouter(
            mock(DocumentStructureGraphService.class),
            navigationIndexProvider,
            queryUnderstandingProvider
        );
    }
}
