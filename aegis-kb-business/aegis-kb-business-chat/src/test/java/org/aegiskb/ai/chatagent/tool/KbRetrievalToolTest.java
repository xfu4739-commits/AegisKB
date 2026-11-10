package org.aegiskb.ai.chatagent.tool;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants;
import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionRequest;
import org.aegiskb.ai.chatagent.rag.model.RetrievalPlan;
import org.aegiskb.ai.chatagent.rag.service.RagRerankService;
import org.aegiskb.ai.chatagent.rag.service.RagRetrievalEngine;
import org.aegiskb.ai.chatagent.rag.retrieve.channel.RetrievalChannel;
import org.aegiskb.ai.chatagent.rag.retrieve.channel.RetrievalChannelResult;
import org.aegiskb.ai.chatagent.support.ChatContextKeys;
import org.aegiskb.ai.chatagent.support.StreamEventWriter;
import org.aegiskb.ai.manage.service.DocumentKnowledgeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S19-P3 目标不变量：kb_search 每次调用产生唯一 execution request，
 * scope 与冻结 Plan 完全一致；越界 scope 与缺失会话 fail closed。
 */
class KbRetrievalToolTest {

    private static final String CHANNEL_ALPHA = "FIXTURE_CHANNEL_ALPHA";

    private ExecutorService executorService;
    private DocumentKnowledgeService documentKnowledgeService;
    private RagRerankService ragRerankService;
    private RecordingChannel alpha;
    private KbRetrievalTool tool;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
        documentKnowledgeService = mock(DocumentKnowledgeService.class);
        when(documentKnowledgeService.elevateToParentBlocks(anyList(), anyInt())).thenReturn(List.of());
        ragRerankService = mock(RagRerankService.class);
        alpha = new RecordingChannel(CHANNEL_ALPHA, request -> List.of(
            RetrievalPlanFixtures.sourceDocument("alpha-doc-1", 101L, 0.91D),
            RetrievalPlanFixtures.sourceDocument("alpha-doc-2", 102L, 0.81D)
        ));
        RagRetrievalEngine engine = new RagRetrievalEngine(
            List.of(alpha),
            new org.aegiskb.ai.chatagent.rag.config.ChatRagProperties(),
            ragRerankService,
            documentKnowledgeService,
            executorService
        );
        tool = new KbRetrievalTool(engine, mock(StreamEventWriter.class));
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    private ToolContext toolContext(KbRetrievalSession session) {
        RunnableConfig config = RunnableConfig.builder().threadId("fixture-thread").build();
        if (session != null) {
            config.context().put(ChatContextKeys.KB_RETRIEVAL_SESSION, session);
        }
        return new ToolContext(Map.of(ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY, config));
    }

    @Test
    void projectionKeepsFrozenScopeAndOnlyReplacesQuery() {
        RetrievalPlan frozenPlan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);
        KbRetrievalSession session = new KbRetrievalSession(frozenPlan);

        KbRetrievalToolResult result = tool.search(new KbRetrievalRequest("agent query one"), toolContext(session));

        assertThat(alpha.receivedRequests).hasSize(1);
        RetrievalExecutionRequest received = alpha.receivedRequests.get(0);
        assertThat(received.subQuestionIndex()).isEqualTo(1);
        assertThat(received.executionQuery()).isEqualTo("agent query one");
        assertThat(received.scopeMode()).isEqualTo(frozenPlan.getScopeMode());
        assertThat(received.knowledgeBaseIds()).containsExactly(RetrievalPlanFixtures.KNOWLEDGE_BASE_ID);
        assertThat(received.allowedDocumentScope()).containsExactly(RetrievalPlanFixtures.DOCUMENT_ID);
        assertThat(received.documentScope()).containsExactly(RetrievalPlanFixtures.DOCUMENT_ID);
        assertThat(received.taskScope()).containsExactly(RetrievalPlanFixtures.TASK_ID);
        assertThat(received.channels()).hasSize(1);
        assertThat(received.channels().get(0).channelName()).isEqualTo(CHANNEL_ALPHA);
        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.callIndex()).isEqualTo(1);
        assertThat(result.candidates()).hasSize(2);
        assertThat(session.getStageResults()).hasSize(1);
        assertThat(session.getUsedChannels()).containsExactly(CHANNEL_ALPHA);
    }

    @Test
    void sequentialCallsIncrementIndexAndAccumulateStageResults() {
        RetrievalPlan frozenPlan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);
        KbRetrievalSession session = new KbRetrievalSession(frozenPlan);
        ToolContext context = toolContext(session);

        KbRetrievalToolResult first = tool.search(new KbRetrievalRequest("agent query one"), context);
        KbRetrievalToolResult second = tool.search(new KbRetrievalRequest("agent query two"), context);

        assertThat(first.callIndex()).isEqualTo(1);
        assertThat(second.callIndex()).isEqualTo(2);
        assertThat(alpha.receivedRequests).hasSize(2);
        assertThat(alpha.receivedRequests)
            .extracting(RetrievalExecutionRequest::subQuestionIndex)
            .containsExactly(1, 2);
        assertThat(alpha.receivedRequests)
            .extracting(RetrievalExecutionRequest::executionQuery)
            .containsExactly("agent query one", "agent query two");
        assertThat(alpha.receivedRequests)
            .allSatisfy(request -> assertThat(request.documentScope())
                .containsExactly(RetrievalPlanFixtures.DOCUMENT_ID));
        assertThat(session.getStageResults()).hasSize(2);
    }

    @Test
    void blankQueryFailsClosed() {
        KbRetrievalSession session = new KbRetrievalSession(RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA));

        assertThatThrownBy(() -> tool.search(new KbRetrievalRequest("   "), toolContext(session)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> tool.search(null, toolContext(session)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(alpha.receivedRequests).isEmpty();
    }

    @Test
    void missingSessionFailsClosed() {
        assertThatThrownBy(() -> tool.search(new KbRetrievalRequest("agent query"), toolContext(null)))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> tool.search(new KbRetrievalRequest("agent query"), new ToolContext(Map.of())))
            .isInstanceOf(IllegalStateException.class);
        assertThat(alpha.receivedRequests).isEmpty();
    }

    @Test
    void outOfScopeFrozenPlanFailsClosedAtSessionConstruction() {
        RetrievalPlan tamperedPlan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);
        tamperedPlan.setDocumentScope(List.of(RetrievalPlanFixtures.DOCUMENT_ID + 999L));

        assertThatThrownBy(() -> new KbRetrievalSession(tamperedPlan))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("allowed document scope");
        assertThatThrownBy(() -> new KbRetrievalSession(null))
            .isInstanceOf(NullPointerException.class);
    }

    private static final class RecordingChannel implements RetrievalChannel {

        private final String name;
        private final Function<RetrievalExecutionRequest, List<Document>> supplier;
        private final List<RetrievalExecutionRequest> receivedRequests =
            Collections.synchronizedList(new ArrayList<>());

        private RecordingChannel(String name, Function<RetrievalExecutionRequest, List<Document>> supplier) {
            this.name = name;
            this.supplier = supplier;
        }

        @Override
        public String channelName() {
            return name;
        }

        @Override
        public RetrievalChannelResult retrieve(RetrievalExecutionRequest request) {
            receivedRequests.add(request);
            return new RetrievalChannelResult(name, new ArrayList<>(supplier.apply(request)));
        }
    }
}
