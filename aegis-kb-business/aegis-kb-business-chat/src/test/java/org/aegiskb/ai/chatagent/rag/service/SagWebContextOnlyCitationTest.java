package org.aegiskb.ai.chatagent.rag.service;

import org.aegiskb.ai.chatagent.model.SearchReference;
import org.aegiskb.ai.chatagent.rag.config.ChatRagProperties;
import org.aegiskb.ai.chatagent.rag.fixture.RetrievalPlanFixtures;
import org.aegiskb.ai.chatagent.rag.model.ConversationExecutionPlan;
import org.aegiskb.ai.chatagent.rag.model.PromptReferenceDecision;
import org.aegiskb.ai.chatagent.rag.model.PromptReferenceDisposition;
import org.aegiskb.ai.chatagent.rag.model.RagPromptAssemblyResult;
import org.aegiskb.ai.chatagent.rag.model.RagRetrievalContext;
import org.aegiskb.ai.chatagent.rag.model.SubQuestionEvidence;
import org.aegiskb.ai.chatagent.rag.support.SearchReferenceMapper;
import org.aegiskb.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.aegiskb.ai.prompt.PromptTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S19-P5 目标不变量：web（tavily_search）证据只渲染为 Context Only，且显式 citation 守恒。
 *
 * <p>覆盖：web 证据渲染为 {@code PROMPT_RENDERED_CONTEXT} 且不出现在 rendered/explicit Source identities；
 * 答案无合法 {@code [n]} token 时 binding 数组为空；有 token 时与 rendered source identities 同序守恒；
 * 指向 web 的编号 token 不可绑定（web 无引用资格）。</p>
 */
class SagWebContextOnlyCitationTest {

    private static final String WEB_URL = "https://example.com/external-regulation";

    @Test
    void webEvidenceIsContextOnlyAndCitationConserves() {
        RagPromptAssemblyService assemblyService = assemblyService();
        SearchReference kbReference = SearchReferenceMapper.fromDocument(
            RetrievalPlanFixtures.sourceDocument("doc-1", 100L, 0.9D), 1, "fixture normalized query", 1);
        String kbIdentity = kbReference.uniqueKey();

        RagRetrievalContext context = new RagRetrievalContext();
        context.setRetrievalQuestion("fixture normalized query");
        SubQuestionEvidence evidence = new SubQuestionEvidence();
        evidence.setSubQuestionIndex(1);
        evidence.setSubQuestion("fixture normalized query");
        evidence.setReferences(new ArrayList<>(List.of(kbReference)));
        evidence.setContextDocuments(new ArrayList<>(List.of(webContextDocument())));
        context.setSubQuestionEvidenceList(new ArrayList<>(List.of(evidence)));

        ConversationExecutionPlan plan = ConversationExecutionPlan.builder()
            .originalQuestion("fixture question")
            .currentDateText("2026-11-10")
            .retrievalPlan(RetrievalPlanFixtures.singleQueryPlan("FIXTURE_CHANNEL_ALPHA"))
            .build();

        RagPromptAssemblyResult result = assemblyService.assemble(plan, context);

        assertThat(result.getRenderedSourceIdentities()).containsExactly(kbIdentity);
        assertThat(result.getRenderedContextIdentities()).containsExactly("WEB:" + WEB_URL);
        assertThat(result.getRenderedSourceIdentities()).doesNotContain("WEB:" + WEB_URL);
        assertThat(dispositionByIdentity(result.getReferenceManifest(), kbIdentity))
            .isEqualTo(PromptReferenceDisposition.PROMPT_RENDERED_SOURCE);
        assertThat(dispositionByIdentity(result.getReferenceManifest(), "WEB:" + WEB_URL))
            .isEqualTo(PromptReferenceDisposition.PROMPT_RENDERED_CONTEXT);

        ExplicitCitationBindingService bindingService = new ExplicitCitationBindingService();

        List<SearchReference> noToken = bindingService.bind("这是一段没有任何引用编号的回答。", result, null, "SAG");
        assertThat(noToken).isEmpty();

        List<SearchReference> bound = bindingService.bind("内部流程符合监管要求 [1]。", result, null, "SAG");
        assertThat(bound).hasSize(1);
        assertThat(bound.get(0).uniqueKey()).isEqualTo(kbIdentity);
        assertThat(bound.stream().map(SearchReference::uniqueKey).toList()).containsExactly(kbIdentity);

        List<SearchReference> webToken = bindingService.bind("仅凭联网结果 [2] 下结论。", result, null, "SAG");
        assertThat(webToken).isEmpty();
    }

    private PromptReferenceDisposition dispositionByIdentity(List<PromptReferenceDecision> manifest, String identity) {
        return manifest.stream()
            .filter(decision -> identity.equals(decision.identity()))
            .map(PromptReferenceDecision::disposition)
            .findFirst()
            .orElse(null);
    }

    private Document webContextDocument() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DocumentKnowledgeMetadataKeys.SOURCE_TYPE, "WEB");
        metadata.put(DocumentKnowledgeMetadataKeys.URL, WEB_URL);
        metadata.put(DocumentKnowledgeMetadataKeys.TITLE, "外部监管要求");
        metadata.put(DocumentKnowledgeMetadataKeys.TOOL_NAME, "tavily_search");
        metadata.put(DocumentKnowledgeMetadataKeys.CONTEXT_ONLY, true);
        return Document.builder()
            .id("web-context-" + WEB_URL)
            .text("外部监管要求的最新时效性内容。")
            .metadata(metadata)
            .build();
    }

    private RagPromptAssemblyService assemblyService() {
        PromptTemplateService promptTemplateService = mock(PromptTemplateService.class);
        when(promptTemplateService.render(anyString(), any())).thenReturn("RENDERED_BLOCK");
        return new RagPromptAssemblyService(new ChatRagProperties(), promptTemplateService);
    }
}
