package org.aegiskb.ai.chatagent.rag.service;

import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
import org.aegiskb.ai.chatagent.rag.model.FinalEvidenceDecision.RerankStatus;
import org.aegiskb.ai.chatagent.rag.model.RetrievalExecutionRequest;
import org.aegiskb.ai.chatagent.rag.model.RetrievalPlan;
import org.aegiskb.ai.chatagent.rag.retrieve.channel.RetrievalChannel;
import org.aegiskb.ai.chatagent.rag.retrieve.channel.RetrievalChannelResult;
import org.aegiskb.ai.manage.service.DocumentKnowledgeService;
import org.aegiskb.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S19-P2 目标不变量：per-call 检索阶段入口只执行 channel→fusion→rerank window，不做 final selection。
 */
class RagRetrievalEngineStageSplitTest {

    private static final String CHANNEL_ALPHA = "FIXTURE_CHANNEL_ALPHA";

    private ExecutorService executorService;
    private DocumentKnowledgeService documentKnowledgeService;
    private RagRerankService ragRerankService;

    @BeforeEach
    void setUp() {
        executorService = Executors.newFixedThreadPool(4);
        documentKnowledgeService = mock(DocumentKnowledgeService.class);
        when(documentKnowledgeService.elevateToParentBlocks(anyList(), anyInt())).thenReturn(List.of());
        ragRerankService = mock(RagRerankService.class);
    }

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    private RagRetrievalEngine engine(List<RetrievalChannel> channels) {
        return new RagRetrievalEngine(
            channels,
            new org.aegiskb.ai.chatagent.rag.config.ChatRagProperties(),
            ragRerankService,
            documentKnowledgeService,
            executorService
        );
    }

    @Test
    void stageEntryExecutesChannelsAndRerankWindowWithoutFinalSelection() {
        RecordingChannel alpha = new RecordingChannel(CHANNEL_ALPHA, request -> List.of(
            RetrievalPlanFixtures.sourceDocument("alpha-doc-1", 101L, 0.91D),
            RetrievalPlanFixtures.sourceDocument("alpha-doc-2", 102L, 0.81D)
        ));
        RetrievalPlan plan = RetrievalPlanFixtures.singleQueryPlan(CHANNEL_ALPHA);
        RetrievalExecutionRequest request = RetrievalExecutionRequest.compile(plan).get(0);
        List<String> usedChannels = Collections.synchronizedList(new ArrayList<>());
        List<String> notes = Collections.synchronizedList(new ArrayList<>());

        RagRetrievalEngine.RetrievalStageResult stage =
            engine(List.of(alpha)).executeRetrievalStagesAsync(request, plan, usedChannels, notes).join();

        assertThat(alpha.receivedRequests).containsExactly(request);
        assertThat(stage.noChannels()).isFalse();
        assertThat(stage.subQuestionIndex()).isEqualTo(1);
        assertThat(stage.rerankResult().candidates()).hasSize(2);
        assertThat(stage.rerankResult().candidates())
            .allSatisfy(document -> assertThat(document.getMetadata())
                .containsEntry(DocumentKnowledgeMetadataKeys.RERANK_STATUS, RerankStatus.NOT_REQUESTED.name())
                .doesNotContainKey(DocumentKnowledgeMetadataKeys.FINAL_SELECTION_REASON));
        assertThat(stage.candidatePools().contextDocuments()).isEmpty();
        assertThat(stage.candidatePools().sourceDocuments()).hasSize(2);
        assertThat(stage.rankingWindow().buildFailed()).isFalse();
        assertThat(stage.channelTraces()).isNotEmpty();
        assertThat(usedChannels).containsExactly(CHANNEL_ALPHA);
        assertThat(notes).isEmpty();
    }

    @Test
    void stageEntryMarksNoChannelsWhenNoChannelEnabled() {
        RetrievalPlan plan = RetrievalPlanFixtures.plan(
            List.of(RetrievalPlanFixtures.executionQuery(1, "fixture execution query")),
            List.of(RetrievalPlanFixtures.disabledChannelPlan(CHANNEL_ALPHA))
        );
        RetrievalExecutionRequest request = RetrievalExecutionRequest.compile(plan).get(0);
        List<String> usedChannels = Collections.synchronizedList(new ArrayList<>());
        List<String> notes = Collections.synchronizedList(new ArrayList<>());

        RagRetrievalEngine.RetrievalStageResult stage =
            engine(List.of()).executeRetrievalStagesAsync(request, plan, usedChannels, notes).join();

        assertThat(stage.noChannels()).isTrue();
        assertThat(stage.rawChannelResults()).isEmpty();
        assertThat(stage.rerankResult().candidates()).isEmpty();
        assertThat(stage.rankingWindow().includedDocuments()).isEmpty();
        assertThat(stage.candidatePools().sourceDocuments()).isEmpty();
        assertThat(usedChannels).isEmpty();
        assertThat(notes).anySatisfy(note -> assertThat(note).contains("没有可用的检索通道"));
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
