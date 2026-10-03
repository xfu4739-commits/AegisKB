package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.raptor.RaptorSearchResult;

import java.util.List;

public interface RaptorSearchService {

    List<RaptorSearchResult> search(String question, List<Long> documentIds, List<Long> taskIds, int topK, int sourceChunkTopK);
}
