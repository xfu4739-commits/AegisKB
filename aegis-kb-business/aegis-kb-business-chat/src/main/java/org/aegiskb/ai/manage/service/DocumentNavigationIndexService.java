package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentDocumentStructureNode;

import java.util.List;

public interface DocumentNavigationIndexService {

    void reindexDocumentNodes(Long documentId, Long parseTaskId, List<SuperAgentDocumentStructureNode> nodes);

    void deleteByDocumentId(Long documentId);

    List<NavigationSectionHit> searchSections(Long documentId,
                                              String topic,
                                              String facet,
                                              String informationNeed,
                                              String question,
                                              int size);

    record NavigationSectionHit(
        Long nodeId,
        String nodeCode,
        String title,
        String sectionPath,
        String canonicalPath,
        double score
    ) {
    }
}
