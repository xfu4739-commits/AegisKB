package org.javaup.ai.manage.service;

import org.javaup.ai.manage.data.SuperAgentDocument;
import org.javaup.ai.manage.data.SuperAgentDocumentBlock;
import org.javaup.ai.manage.data.SuperAgentDocumentStrategyPlan;
import org.javaup.ai.manage.data.SuperAgentDocumentStrategyStep;
import org.javaup.ai.manage.support.ChunkCandidate;
import org.javaup.ai.manage.support.DocumentAnalysisResult;
import org.javaup.ai.manage.support.DocumentStrategyPlanDraft;
import org.javaup.ai.manage.support.ParentBlockCandidate;

import java.util.List;

public interface DocumentStrategyService {

    DocumentStrategyPlanDraft recommendStrategy(SuperAgentDocument document, DocumentAnalysisResult analysisResult);

    List<SuperAgentDocumentStrategyStep> normalizeSteps(SuperAgentDocumentStrategyPlan basePlan,
                                                        List<SuperAgentDocumentStrategyStep> baseSteps,
                                                        List<Integer> requestParentStrategyTypes,
                                                        List<Integer> requestChildStrategyTypes,
                                                        Long documentId);

    List<ParentBlockCandidate> buildParentBlocks(SuperAgentDocument document,
                                                 SuperAgentDocumentStrategyPlan plan,
                                                 List<SuperAgentDocumentStrategyStep> steps,
                                                 List<SuperAgentDocumentBlock> documentBlocks);

    /**
     * A1：用新的 keywords/questions 覆盖切块后重算加权正文（{@code [KEYWORDS]/[QUESTIONS]} 段），
     * 复用与切块期一致的拼接逻辑，保证 contentWithWeight 只有一处组装规则。构建期回填 auto keyword/question 时调用。
     *
     * @param candidate     待回填切块（keywords/questions 已被覆盖为新值）
     * @param keywordsJson  新 keywords（JSON 数组字符串，可空）
     * @param questionsJson 新 questions（JSON 数组字符串，可空）
     * @return 重算后的 contentWithWeight
     */
    String rebuildContentWithWeight(ChunkCandidate candidate, String keywordsJson, String questionsJson);
}
