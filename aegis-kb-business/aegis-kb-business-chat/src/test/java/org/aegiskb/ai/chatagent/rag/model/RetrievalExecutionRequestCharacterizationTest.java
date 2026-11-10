package org.aegiskb.ai.chatagent.rag.model;

import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S19-P1 characterization：冻结 RetrievalExecutionRequest 的编译与对账契约。
 *
 * <p>SAG 分支的 kb_search per-call 投影必须复用该契约；未入 Plan 的查询必须 fail closed。</p>
 */
class RetrievalExecutionRequestCharacterizationTest {

    private static final String CHANNEL_ALPHA = "FIXTURE_CHANNEL_ALPHA";

    private RetrievalPlan plan() {
        return RetrievalPlanFixtures.plan(
            List.of(
                RetrievalPlanFixtures.executionQuery(1, "fixture execution query one"),
                RetrievalPlanFixtures.executionQuery(2, "fixture execution query two")
            ),
            List.of(RetrievalPlanFixtures.channelPlan(CHANNEL_ALPHA))
        );
    }

    @Test
    void compileProducesOneRequestPerPlannedQueryWithVerbatimProjection() {
        RetrievalPlan plan = plan();

        List<RetrievalExecutionRequest> requests = RetrievalExecutionRequest.compile(plan);

        assertThat(requests).hasSize(2);
        assertThat(requests).extracting(RetrievalExecutionRequest::subQuestionIndex).containsExactly(1, 2);
        assertThat(requests).extracting(RetrievalExecutionRequest::executionQuery)
            .containsExactly("fixture execution query one", "fixture execution query two");
        for (RetrievalExecutionRequest request : requests) {
            assertThat(request.scopeMode()).isEqualTo(plan.getScopeMode());
            assertThat(request.knowledgeBaseIds()).containsExactly(RetrievalPlanFixtures.KNOWLEDGE_BASE_ID);
            assertThat(request.allowedDocumentScope()).containsExactly(RetrievalPlanFixtures.DOCUMENT_ID);
            assertThat(request.documentScope()).containsExactly(RetrievalPlanFixtures.DOCUMENT_ID);
            assertThat(request.taskScope()).containsExactly(RetrievalPlanFixtures.TASK_ID);
            assertThat(request.enabledChannels()).hasSize(1);
            RetrievalExecutionRequest.ChannelSpec channel = request.requireChannel(CHANNEL_ALPHA);
            assertThat(channel.enabled()).isTrue();
            assertThat(channel.topK()).isEqualTo(10);
            assertThat(channel.budget()).isEqualTo(5);
            assertThat(channel.timeoutMs()).isEqualTo(2000L);
            assertThat(request.tableIntent().requested()).isFalse();
            assertThat(request.graphIntent().requested()).isFalse();
            assertThat(request.raptorIntent().requested()).isFalse();
        }
    }

    @Test
    void reconcileReportsNoDifferenceForCompiledRequests() {
        RetrievalPlan plan = plan();

        List<RetrievalExecutionRequest> requests = RetrievalExecutionRequest.compile(plan);

        assertThat(requests.stream().map(request -> request.reconcile(plan)))
            .allSatisfy(reconciliation -> assertThat(reconciliation.differences()).isEmpty());
    }

    @Test
    void compileRejectsQueryAbsentFromPlan() {
        RetrievalPlan plan = plan();
        RetrievalExecutionQuery unplanned = RetrievalExecutionQuery.builder()
            .index(3)
            .sourceQuestion("fixture question")
            .normalizedQuery("fixture normalized query")
            .executionQuery("unplanned fixture query")
            .build();

        assertThatThrownBy(() -> RetrievalExecutionRequest.compile(plan, unplanned))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("absent from RetrievalPlan");
    }

    @Test
    void compileRejectsTamperedQueryText() {
        RetrievalPlan plan = plan();
        RetrievalExecutionQuery tampered = RetrievalExecutionQuery.builder()
            .index(1)
            .sourceQuestion("fixture question")
            .normalizedQuery("fixture normalized query")
            .executionQuery("tampered fixture query")
            .build();

        assertThatThrownBy(() -> RetrievalExecutionRequest.compile(plan, tampered))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match RetrievalPlan question plan");
    }
}
