package org.aegiskb.memory.model;

import java.util.List;

public record MemoryComparisonResponse(
    String scriptName,
    List<ComparisonTurnResponse> turns,
    MemoryChatResponse slidingWindowFinalState,
    MemoryChatResponse summaryFinalState
) {
}
