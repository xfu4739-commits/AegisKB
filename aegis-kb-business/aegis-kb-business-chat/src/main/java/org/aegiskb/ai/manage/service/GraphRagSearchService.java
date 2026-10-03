package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.graph.GraphRagSearchResult;

import java.util.List;

public interface GraphRagSearchService {

    List<GraphRagSearchResult> search(String question,
                                      List<Long> documentIds,
                                      List<Long> taskIds,
                                      int topK,
                                      int maxHops);
}
