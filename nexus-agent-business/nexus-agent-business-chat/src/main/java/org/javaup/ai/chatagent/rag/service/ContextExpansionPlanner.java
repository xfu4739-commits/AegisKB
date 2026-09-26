package org.javaup.ai.chatagent.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.javaup.ai.chatagent.rag.config.ChatRagProperties;
import org.javaup.ai.chatagent.rag.model.ConversationStructureAnchor;
import org.javaup.ai.chatagent.rag.model.ContextCandidateDisposition;
import org.javaup.ai.chatagent.rag.model.DocumentNavigationAction;
import org.javaup.ai.chatagent.rag.model.EvidenceCandidatePools;
import org.javaup.ai.chatagent.rag.model.RetrievalIntent;
import org.javaup.ai.chatagent.rag.model.RetrievalPlan;
import org.javaup.ai.chatagent.rag.model.StructureNavigationOperation;
import org.javaup.ai.chatagent.rag.model.StructureNavigationResult;
import org.javaup.ai.chatagent.rag.support.EvidenceCandidateNormalizer;
import org.javaup.ai.chatagent.rag.support.EvidenceCandidateIdentity;
import org.javaup.ai.chatagent.rag.support.EvidenceIdentityResolver;
import org.javaup.ai.manage.data.SuperAgentDocumentStructureNode;
import org.javaup.ai.manage.model.StructureAnchoredEvidenceRequest;
import org.javaup.ai.manage.service.DocumentKnowledgeService;
import org.javaup.ai.manage.support.DocumentKnowledgeMetadataKeys;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 上下文扩展规划：组织回答阶段的 prompt context 候选，与可引用证据选择分离。
 *
 * <p>负责两类结构确定性上下文——结构锚点正文扩展（下钻 ParentBlock/正文 chunk）与结构导航确定性上下文
 * （current/parent/sibling/child 节点）。这些候选可进 rerank / prompt context，其可引用性仍由证据身份
 * （{@link EvidenceCandidateNormalizer} / EvidenceIdentityResolver）决定，本类不判定 citation。</p>
 */
@Slf4j
public class ContextExpansionPlanner {

    private static final int STRUCTURE_ANCHOR_MAX_PER_ANCHOR = 2;
    private static final int STRUCTURE_ANCHOR_MAX_TOTAL = 4;
    private static final String PARENT_BLOCK_CONTEXT = "PARENT_BLOCK";
    private static final String GRAPH_WRAPPER_CONTEXT = "GRAPH_WRAPPER";
    private static final String RAPTOR_WRAPPER_CONTEXT = "RAPTOR_WRAPPER";
    private static final String STRUCTURE_NAVIGATION_CONTEXT = "STRUCTURE_NAVIGATION";
    private static final String STRUCTURE_ANCHOR_SOURCE_CHANNEL = "structure-anchor";
    private static final String UNRESOLVED_CONTEXT = "UNRESOLVED";

    private final DocumentKnowledgeService documentKnowledgeService;
    private final ChatRagProperties properties;

    public ContextExpansionPlanner(DocumentKnowledgeService documentKnowledgeService, ChatRagProperties properties) {
        this.documentKnowledgeService = documentKnowledgeService;
        this.properties = properties;
    }

    public List<Document> expandStructureAnchoredEvidence(List<Document> parentCandidates,
                                                          RetrievalPlan plan,
                                                          List<String> notes,
                                                          int subQuestionIndex) {
        StructureAnchoredEvidenceRequest request = buildStructureAnchoredEvidenceRequest(parentCandidates, plan);
        if (request == null) {
            return List.of();
        }
        try {
            List<Document> expanded = documentKnowledgeService.expandStructureAnchoredEvidence(request);
            if (expanded == null || expanded.isEmpty()) {
                return List.of();
            }
            notes.add("子问题" + subQuestionIndex + "结构锚点正文扩展命中 " + expanded.size() + " 条。");
            return expanded;
        }
        catch (RuntimeException exception) {
            log.warn("结构锚点正文扩展失败: subQuestionIndex={}, message={}", subQuestionIndex, exception.getMessage(), exception);
            notes.add("子问题" + subQuestionIndex + "结构锚点正文扩展失败，已保留普通检索候选继续回答。");
            return List.of();
        }
    }

    private StructureAnchoredEvidenceRequest buildStructureAnchoredEvidenceRequest(List<Document> parentCandidates,
                                                                                   RetrievalPlan plan) {
        List<Long> documentIds = resolvePlanDocumentIds(plan);
        List<Long> taskIds = resolvePlanTaskIds(plan);
        if (documentIds.isEmpty() || taskIds.isEmpty()) {
            return null;
        }

        LinkedHashSet<Long> structureNodeIds = new LinkedHashSet<>();
        LinkedHashSet<Long> sourceHeadingNodeIds = new LinkedHashSet<>();
        LinkedHashSet<String> canonicalPaths = new LinkedHashSet<>();
        LinkedHashSet<String> sectionAnchors = new LinkedHashSet<>();
        collectPlanStructureAnchors(plan, structureNodeIds, sourceHeadingNodeIds, canonicalPaths, sectionAnchors);
        collectCandidateStructureAnchors(parentCandidates, structureNodeIds, canonicalPaths, sectionAnchors);
        if (structureNodeIds.isEmpty() && canonicalPaths.isEmpty() && sectionAnchors.isEmpty()) {
            return null;
        }

        return StructureAnchoredEvidenceRequest.builder()
            .candidateDocuments(parentCandidates == null ? List.of() : parentCandidates)
            .structureNodeIds(new ArrayList<>(structureNodeIds))
            .sourceHeadingNodeIds(new ArrayList<>(sourceHeadingNodeIds))
            .canonicalPaths(new ArrayList<>(canonicalPaths))
            .sectionAnchors(new ArrayList<>(sectionAnchors))
            .documentIds(documentIds)
            .taskIds(taskIds)
            .knowledgeBaseIds(plan == null || plan.getKnowledgeBaseIds() == null
                ? List.of()
                : plan.getKnowledgeBaseIds().stream().filter(Objects::nonNull).distinct().toList())
            .maxPerAnchor(STRUCTURE_ANCHOR_MAX_PER_ANCHOR)
            .maxTotal(STRUCTURE_ANCHOR_MAX_TOTAL)
            .maxChars(properties.getParentEvidenceMaxChars())
            .build();
    }

    private void collectPlanStructureAnchors(RetrievalPlan plan,
                                             LinkedHashSet<Long> structureNodeIds,
                                             LinkedHashSet<Long> sourceHeadingNodeIds,
                                             LinkedHashSet<String> canonicalPaths,
                                             LinkedHashSet<String> sectionAnchors) {
        if (plan == null) {
            return;
        }
        collectStructureNavigationResultAnchors(plan, structureNodeIds, canonicalPaths, sectionAnchors);
        StructureNavigationResult navigationResult = plan.getStructureNavigationResult();
        if (navigationResult != null && navigationResult.isDeterministic()) {
            sourceHeadingNodeIds.addAll(structureNodeIds);
        }
        ConversationStructureAnchor structureAnchor = plan.getStructureAnchor();
        if (structureAnchor != null && !structureAnchor.isEmpty()) {
            if (structureAnchor.getStructureNodeId() != null) {
                structureNodeIds.add(structureAnchor.getStructureNodeId());
            }
            if (!safeText(structureAnchor.getCanonicalPath()).isBlank()) {
                canonicalPaths.add(structureAnchor.getCanonicalPath());
            }
            if (!safeText(structureAnchor.getTargetSectionHint()).isBlank()) {
                sectionAnchors.add(structureAnchor.getTargetSectionHint());
            }
            if (!safeText(structureAnchor.getRootSectionCode()).isBlank()) {
                sectionAnchors.add(structureAnchor.getRootSectionCode());
            }
        }
        if (plan.getMetadataFilters() != null && plan.getMetadataFilters().getSectionPathHints() != null) {
            plan.getMetadataFilters().getSectionPathHints().stream()
                .map(this::safeText)
                .filter(anchor -> !anchor.isBlank())
                .forEach(sectionAnchors::add);
        }
    }

    private void collectStructureNavigationResultAnchors(RetrievalPlan plan,
                                                         LinkedHashSet<Long> structureNodeIds,
                                                         LinkedHashSet<String> canonicalPaths,
                                                         LinkedHashSet<String> sectionAnchors) {
        StructureNavigationResult result = plan == null ? null : plan.getStructureNavigationResult();
        if (result == null) {
            return;
        }
        DocumentNavigationAction action = plan.getNavigationAction();
        if (action == DocumentNavigationAction.CHILD_SECTION_DESCEND) {
            if (result.getDirectChildren() != null) {
                result.getDirectChildren().forEach(node -> collectStructureNavigationNodeAnchor(node, structureNodeIds, canonicalPaths, sectionAnchors));
            }
            collectStructureNavigationNodeAnchor(result.getCurrent(), structureNodeIds, canonicalPaths, sectionAnchors);
            return;
        }
        if (action == DocumentNavigationAction.ANCESTOR_SECTION_RETURN) {
            collectStructureNavigationNodeAnchor(result.getParent(), structureNodeIds, canonicalPaths, sectionAnchors);
            collectStructureNavigationNodeAnchor(result.getCurrent(), structureNodeIds, canonicalPaths, sectionAnchors);
            return;
        }
        if (action == DocumentNavigationAction.SECTION_ADJACENCY_LOOKUP) {
            List<StructureNavigationOperation> operations = plan.getStructureNavigation() == null
                || plan.getStructureNavigation().getOperations() == null
                ? List.of()
                : plan.getStructureNavigation().getOperations();
            boolean previousOnly = operations.contains(StructureNavigationOperation.PREVIOUS_SIBLING)
                && !operations.contains(StructureNavigationOperation.NEXT_SIBLING);
            boolean nextOnly = operations.contains(StructureNavigationOperation.NEXT_SIBLING)
                && !operations.contains(StructureNavigationOperation.PREVIOUS_SIBLING);
            if (nextOnly) {
                collectStructureNavigationNodeAnchor(result.getNextSibling(), structureNodeIds, canonicalPaths, sectionAnchors);
            }
            else if (previousOnly) {
                collectStructureNavigationNodeAnchor(result.getPreviousSibling(), structureNodeIds, canonicalPaths, sectionAnchors);
            }
            else {
                collectStructureNavigationNodeAnchor(result.getPreviousSibling(), structureNodeIds, canonicalPaths, sectionAnchors);
                collectStructureNavigationNodeAnchor(result.getNextSibling(), structureNodeIds, canonicalPaths, sectionAnchors);
            }
            collectStructureNavigationNodeAnchor(result.getCurrent(), structureNodeIds, canonicalPaths, sectionAnchors);
            collectStructureNavigationNodeAnchor(result.getParent(), structureNodeIds, canonicalPaths, sectionAnchors);
            return;
        }
        collectStructureNavigationNodeAnchor(result.getCurrent(), structureNodeIds, canonicalPaths, sectionAnchors);
        collectStructureNavigationNodeAnchor(result.getParent(), structureNodeIds, canonicalPaths, sectionAnchors);
        collectStructureNavigationNodeAnchor(result.getPreviousSibling(), structureNodeIds, canonicalPaths, sectionAnchors);
        collectStructureNavigationNodeAnchor(result.getNextSibling(), structureNodeIds, canonicalPaths, sectionAnchors);
        if (result.getDirectChildren() != null) {
            result.getDirectChildren().forEach(node -> collectStructureNavigationNodeAnchor(node, structureNodeIds, canonicalPaths, sectionAnchors));
        }
    }

    private void collectStructureNavigationNodeAnchor(SuperAgentDocumentStructureNode node,
                                                      LinkedHashSet<Long> structureNodeIds,
                                                      LinkedHashSet<String> canonicalPaths,
                                                      LinkedHashSet<String> sectionAnchors) {
        if (node == null) {
            return;
        }
        if (node.getId() != null) {
            structureNodeIds.add(node.getId());
        }
        if (!safeText(node.getCanonicalPath()).isBlank()) {
            canonicalPaths.add(node.getCanonicalPath());
        }
        if (!safeText(node.getSectionPath()).isBlank()) {
            sectionAnchors.add(node.getSectionPath());
        }
    }

    private void collectCandidateStructureAnchors(List<Document> candidates,
                                                  LinkedHashSet<Long> structureNodeIds,
                                                  LinkedHashSet<String> canonicalPaths,
                                                  LinkedHashSet<String> sectionAnchors) {
        if (candidates == null || candidates.isEmpty()) {
            return;
        }
        for (Document candidate : candidates) {
            if (candidate == null || candidate.getMetadata() == null) {
                continue;
            }
            Long structureNodeId = metadataLong(candidate.getMetadata(), DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID);
            if (structureNodeId != null) {
                structureNodeIds.add(structureNodeId);
            }
            String canonicalPath = safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.CANONICAL_PATH));
            if (!canonicalPath.isBlank()) {
                canonicalPaths.add(canonicalPath);
            }
            String sectionPath = safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.SECTION_PATH));
            if (!sectionPath.isBlank()) {
                sectionAnchors.add(sectionPath);
            }
        }
    }

    private List<Long> resolvePlanDocumentIds(RetrievalPlan plan) {
        if (plan == null) {
            return List.of();
        }
        if (plan.getDocumentScope() != null && !plan.getDocumentScope().isEmpty()) {
            return plan.getDocumentScope().stream().filter(Objects::nonNull).distinct().toList();
        }
        if (plan.recommendedDocumentHintId() != null) {
            return List.of(plan.recommendedDocumentHintId());
        }
        if (plan.getAllowedDocumentScope() != null && !plan.getAllowedDocumentScope().isEmpty()) {
            return plan.getAllowedDocumentScope().stream().filter(Objects::nonNull).distinct().toList();
        }
        return List.of();
    }

    private List<Long> resolvePlanTaskIds(RetrievalPlan plan) {
        if (plan == null) {
            return List.of();
        }
        if (plan.getTaskScope() != null && !plan.getTaskScope().isEmpty()) {
            return plan.getTaskScope().stream().filter(Objects::nonNull).distinct().toList();
        }
        if (plan.recommendedTaskHintId() != null) {
            return List.of(plan.recommendedTaskHintId());
        }
        return List.of();
    }

    public List<Document> mergeStructureAnchorCandidates(List<Document> primaryCandidates, List<Document> structureCandidates) {
        if ((structureCandidates == null || structureCandidates.isEmpty())) {
            return primaryCandidates == null ? List.of() : primaryCandidates;
        }
        List<Document> primary = primaryCandidates == null ? List.of() : primaryCandidates;
        List<Document> merged = new ArrayList<>();
        for (Document structureCandidate : structureCandidates) {
            if (!isStructureSourceHeading(structureCandidate)) {
                continue;
            }
            Document existing = primary.stream()
                .filter(candidate -> EvidenceCandidateNormalizer.sameEvidenceIdentity(candidate, structureCandidate))
                .findFirst()
                .orElse(structureCandidate);
            mergeRequiredStructureSourceMetadata(existing, structureCandidate);
            if (merged.stream().noneMatch(candidate -> EvidenceCandidateNormalizer.sameEvidenceIdentity(candidate, existing))) {
                merged.add(existing);
            }
        }
        for (Document candidate : primary) {
            if (candidate != null
                && merged.stream().noneMatch(existing -> EvidenceCandidateNormalizer.sameEvidenceIdentity(existing, candidate))) {
                merged.add(candidate);
            }
        }
        for (Document structureCandidate : structureCandidates) {
            if (structureCandidate != null
                && merged.stream().noneMatch(existing -> EvidenceCandidateNormalizer.sameEvidenceIdentity(existing, structureCandidate))) {
                merged.add(structureCandidate);
            }
        }
        return merged;
    }

    private void mergeRequiredStructureSourceMetadata(Document target, Document structureCandidate) {
        if (target == null || structureCandidate == null || target == structureCandidate
            || target.getMetadata() == null || structureCandidate.getMetadata() == null
            || !isStructureSourceHeading(structureCandidate)) {
            return;
        }
        copyMetadata(structureCandidate, target, DocumentKnowledgeMetadataKeys.CHUNK_TYPE);
        copyMetadata(structureCandidate, target, DocumentKnowledgeMetadataKeys.STRUCTURE_ANCHOR_RAW_BODY);
        copyMetadata(structureCandidate, target, DocumentKnowledgeMetadataKeys.SOURCE_AUTHORED_HEADING);
        copyMetadata(structureCandidate, target, DocumentKnowledgeMetadataKeys.STRUCTURE_NAVIGATION_REQUIRED_SOURCE);
        copyMetadata(structureCandidate, target, DocumentKnowledgeMetadataKeys.STRUCTURE_BODY_CANDIDATE_KIND);
    }

    private void copyMetadata(Document source, Document target, String key) {
        if (source.getMetadata().containsKey(key)) {
            target.getMetadata().put(key, source.getMetadata().get(key));
        }
    }

    private boolean isStructureSourceHeading(Document candidate) {
        if (candidate == null || candidate.getMetadata() == null) {
            return false;
        }
        return STRUCTURE_ANCHOR_SOURCE_CHANNEL.equalsIgnoreCase(safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.CHANNEL)))
            && "TITLE".equalsIgnoreCase(safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_TYPE)))
            && Boolean.FALSE.equals(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.STRUCTURE_ANCHOR_RAW_BODY));
    }

    /**
     * Parent elevation 只增加用于理解的 wrapper，不能替换掉拥有真实 citation identity 的 raw child。
     */
    public List<Document> mergeElevatedParentCandidates(List<Document> originalCandidates,
                                                        List<Document> elevatedCandidates) {
        List<Document> original = originalCandidates == null ? List.of() : originalCandidates;
        List<Document> elevated = elevatedCandidates == null ? List.of() : elevatedCandidates;
        LinkedHashSet<String> originalDocumentIds = original.stream()
            .filter(Objects::nonNull)
            .map(Document::getId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        List<Document> merged = new ArrayList<>();
        for (Document candidate : elevated) {
            if (candidate == null) {
                continue;
            }
            if (!originalDocumentIds.contains(candidate.getId())
                && metadataLong(candidate.getMetadata(), DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID) != null) {
                EvidenceCandidateIdentity.assignNew(candidate);
                markContextArtifact(candidate, PARENT_BLOCK_CONTEXT);
            }
            merged.add(candidate);
        }
        for (Document candidate : original) {
            if (candidate != null
                && merged.stream().noneMatch(existing -> EvidenceCandidateNormalizer.sameEvidenceIdentity(existing, candidate))) {
                merged.add(candidate);
            }
        }
        return merged;
    }

    /**
     * 在 evidence budget 前把 Source Evidence 与 Context Only 物理分池。Graph/RAPTOR wrapper 保留为背景，
     * 对应的 source-authored snippet 以独立 Document 投影进入 Source pool。
     */
    public EvidenceCandidatePools partitionCandidates(List<Document> rankedCandidates,
                                                      List<Document> independentContextCandidates) {
        Map<String, Document> sourceByIdentity = new LinkedHashMap<>();
        Map<String, Document> contextByIdentity = new LinkedHashMap<>();
        // 确定性导航结果必须先于普通 wrapper 进入同一 Context Only 预算队列。
        if (independentContextCandidates != null) {
            for (Document candidate : independentContextCandidates) {
                if (candidate == null) {
                    continue;
                }
                markContextArtifact(candidate, STRUCTURE_NAVIGATION_CONTEXT);
                addContext(contextByIdentity, candidate);
            }
        }
        if (rankedCandidates != null) {
            for (Document candidate : rankedCandidates) {
                if (candidate == null) {
                    continue;
                }
                EvidenceCandidateIdentity.ensure(candidate);
                Document sourceProjection = sourceProjection(candidate);
                if (sourceProjection != null) {
                    addSource(sourceByIdentity, sourceProjection);
                }
                if (isDerivedWrapper(candidate) || sourceProjection == null) {
                    Document contextCandidate = sourceProjection == null
                        ? candidate
                        : copyForContext(candidate);
                    markContextArtifact(contextCandidate, contextArtifactType(candidate));
                    addContext(contextByIdentity, contextCandidate);
                }
            }
        }
        return new EvidenceCandidatePools(
            new ArrayList<>(sourceByIdentity.values()),
            new ArrayList<>(contextByIdentity.values())
        );
    }

    private Document sourceProjection(Document candidate) {
        if (candidate.getMetadata() == null
            || candidate.getMetadata().containsKey(DocumentKnowledgeMetadataKeys.CONTEXT_ARTIFACT)) {
            return null;
        }
        String sourceType = safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.SOURCE_TYPE));
        boolean derivedWrapper = "GRAPH_RAG".equalsIgnoreCase(sourceType) || "RAPTOR".equalsIgnoreCase(sourceType);
        if (!derivedWrapper) {
            if (EvidenceCandidateIdentity.isContextCandidate(candidate)) {
                return null;
            }
            EvidenceCandidateNormalizer.enrichIdentity(candidate);
            return candidate;
        }
        if (!EvidenceIdentityResolver.isCitationCapable(candidate)) {
            return null;
        }
        String originalSnippet = safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.ORIGINAL_SNIPPET));
        if (originalSnippet.isBlank()) {
            return null;
        }
        Map<String, Object> sourceMetadata = new LinkedHashMap<>(candidate.getMetadata());
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.CONTEXT_ARTIFACT);
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.CONTEXT_ONLY);
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.SOURCE_EVIDENCE_RESOLVED);
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.CITATION_IDENTITY);
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.CONTEXT_IDENTITY);
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.CITATION_EVIDENCE_TYPE);
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.CANDIDATE_ID);
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.LINEAGE_IDENTITY);
        sourceMetadata.remove(DocumentKnowledgeMetadataKeys.IDENTITY_RESOLUTION_STATUS);
        Document projection = Document.builder()
            .id(candidate.getId() + ":source")
            .text(originalSnippet)
            .metadata(sourceMetadata)
            .score(candidate.getScore())
            .build();
        EvidenceCandidateNormalizer.enrichIdentity(projection);
        return EvidenceIdentityResolver.isCitationCapable(projection) ? projection : null;
    }

    private Document copyForContext(Document candidate) {
        return Document.builder()
            .id(candidate.getId())
            .text(candidate.getText())
            .metadata(new LinkedHashMap<>(candidate.getMetadata()))
            .score(candidate.getScore())
            .build();
    }

    private boolean isDerivedWrapper(Document candidate) {
        if (candidate == null || candidate.getMetadata() == null) {
            return false;
        }
        if (candidate.getMetadata().containsKey(DocumentKnowledgeMetadataKeys.CONTEXT_ARTIFACT)) {
            return true;
        }
        String sourceType = safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.SOURCE_TYPE));
        return "GRAPH_RAG".equalsIgnoreCase(sourceType) || "RAPTOR".equalsIgnoreCase(sourceType);
    }

    private String contextArtifactType(Document candidate) {
        if (candidate != null && candidate.getMetadata() != null) {
            String existing = safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.CONTEXT_ARTIFACT));
            if (!existing.isBlank()) {
                return existing;
            }
            String sourceType = safeText(candidate.getMetadata().get(DocumentKnowledgeMetadataKeys.SOURCE_TYPE));
            if ("GRAPH_RAG".equalsIgnoreCase(sourceType)) {
                return GRAPH_WRAPPER_CONTEXT;
            }
            if ("RAPTOR".equalsIgnoreCase(sourceType)) {
                return RAPTOR_WRAPPER_CONTEXT;
            }
        }
        return UNRESOLVED_CONTEXT;
    }

    private void markContextArtifact(Document candidate, String artifactType) {
        if (candidate == null || candidate.getMetadata() == null) {
            return;
        }
        candidate.getMetadata().put(DocumentKnowledgeMetadataKeys.CONTEXT_ARTIFACT, artifactType);
        candidate.getMetadata().put(
            DocumentKnowledgeMetadataKeys.CONTEXT_DISPOSITION,
            ContextCandidateDisposition.BACKGROUND_AVAILABLE.name()
        );
        candidate.getMetadata().remove(DocumentKnowledgeMetadataKeys.FINAL_SELECTION_REASON);
        EvidenceCandidateNormalizer.enrichIdentity(candidate);
    }

    private void addSource(Map<String, Document> sourceByIdentity, Document source) {
        EvidenceCandidateIdentity.ensure(source);
        String identity = EvidenceIdentityResolver.citationIdentityValue(source);
        String key = identity.isBlank() ? EvidenceCandidateIdentity.candidateId(source) : identity;
        sourceByIdentity.putIfAbsent(key, source);
    }

    private void addContext(Map<String, Document> contextByIdentity, Document context) {
        EvidenceCandidateIdentity.ensure(context);
        String identity = EvidenceIdentityResolver.contextIdentityValue(context);
        if (identity.isBlank()) {
            identity = EvidenceCandidateIdentity.candidateId(context);
        }
        if (!identity.isBlank()) {
            contextByIdentity.putIfAbsent(identity, context);
        }
    }

    public List<Document> buildStructureNavigationContextCandidates(RetrievalPlan plan,
                                                                     List<String> notes,
                                                                     int subQuestionIndex) {
        StructureNavigationResult result = plan == null ? null : plan.getStructureNavigationResult();
        if (plan == null || plan.getPrimaryIntent() != RetrievalIntent.STRUCTURE || result == null || !result.isDeterministic()) {
            return List.of();
        }
        List<Document> candidates = new ArrayList<>();
        DocumentNavigationAction action = plan.getNavigationAction();
        if (action == DocumentNavigationAction.CHILD_SECTION_DESCEND) {
            addStructureNavigationCandidate(candidates, result.getCurrent(), "CURRENT", plan);
            if (result.getDirectChildren() != null) {
                result.getDirectChildren().forEach(node -> addStructureNavigationCandidate(candidates, node, "CHILD", plan));
            }
        }
        else {
            addStructureNavigationCandidate(candidates, result.getCurrent(), "CURRENT", plan);
            addStructureNavigationCandidate(candidates, result.getParent(), "PARENT", plan);
            addStructureNavigationCandidate(candidates, result.getPreviousSibling(), "SIBLING", plan);
            addStructureNavigationCandidate(candidates, result.getNextSibling(), "SIBLING", plan);
        }
        if (!candidates.isEmpty()) {
            notes.add("子问题" + subQuestionIndex + "结构导航确定性上下文命中 " + candidates.size() + " 个节点。");
        }
        return candidates;
    }

    private void addStructureNavigationCandidate(List<Document> candidates,
                                                 SuperAgentDocumentStructureNode node,
                                                 String role,
                                                 RetrievalPlan plan) {
        if (node == null || node.getId() == null) {
            return;
        }
        String documentId = "structure-navigation:" + role + ":" + node.getId();
        if (candidates.stream().anyMatch(candidate -> Objects.equals(candidate.getId(), documentId))) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DocumentKnowledgeMetadataKeys.SOURCE_TYPE, "STRUCTURE_NAVIGATION");
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL, "structure-navigation");
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, structureNavigationScore(role, node));
        metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_ID, firstNonNull(node.getDocumentId(), primaryDocumentId(plan)));
        metadata.put(DocumentKnowledgeMetadataKeys.TASK_ID, firstNonNull(node.getParseTaskId(), primaryTaskId(plan)));
        metadata.put(DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID, node.getId());
        metadata.put(DocumentKnowledgeMetadataKeys.SECTION_PATH, safeText(node.getSectionPath()));
        metadata.put(DocumentKnowledgeMetadataKeys.CANONICAL_PATH, safeText(node.getCanonicalPath()));
        metadata.put(DocumentKnowledgeMetadataKeys.TITLE, safeText(node.getTitle()));
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_TYPE, "TITLE");
        metadata.put(DocumentKnowledgeMetadataKeys.CONTEXT_ONLY, true);
        metadata.put(DocumentKnowledgeMetadataKeys.SOURCE_EVIDENCE_RESOLVED, false);
        Document document = Document.builder()
            .id(documentId)
            .text(buildStructureNavigationText(role, node))
            .metadata(metadata)
            .score(structureNavigationScore(role, node))
            .build();
        candidates.add(document);
    }

    private String buildStructureNavigationText(String role, SuperAgentDocumentStructureNode node) {
        StringBuilder builder = new StringBuilder();
        builder.append("结构导航节点：").append(safeText(node.getTitle()));
        builder.append("\n节点角色：").append(role);
        if (!safeText(node.getSectionPath()).isBlank()) {
            builder.append("\n章节路径：").append(safeText(node.getSectionPath()));
        }
        if (!safeText(node.getCanonicalPath()).isBlank()) {
            builder.append("\ncanonicalPath：").append(safeText(node.getCanonicalPath()));
        }
        return builder.toString();
    }

    private double structureNavigationScore(String role, SuperAgentDocumentStructureNode node) {
        double score = switch (safeText(role)) {
            case "CURRENT" -> 1.40D;
            case "CHILD" -> 1.35D;
            case "PARENT" -> 1.30D;
            case "SIBLING" -> 1.25D;
            default -> 1.0D;
        };
        Integer nodeNo = node == null ? null : node.getNodeNo();
        return nodeNo == null ? score : score - Math.min(0.20D, nodeNo * 0.000001D);
    }

    private Long primaryDocumentId(RetrievalPlan plan) {
        if (plan == null) {
            return null;
        }
        if (plan.recommendedDocumentHintId() != null) {
            return plan.recommendedDocumentHintId();
        }
        return plan.getDocumentScope() == null || plan.getDocumentScope().isEmpty()
            ? null
            : plan.getDocumentScope().get(0);
    }

    private Long primaryTaskId(RetrievalPlan plan) {
        if (plan == null) {
            return null;
        }
        if (plan.recommendedTaskHintId() != null) {
            return plan.recommendedTaskHintId();
        }
        return plan.getTaskScope() == null || plan.getTaskScope().isEmpty()
            ? null
            : plan.getTaskScope().get(0);
    }

    private Long firstNonNull(Long first, Long second) {
        return first == null ? second : first;
    }

    private Long metadataLong(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private String safeText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
