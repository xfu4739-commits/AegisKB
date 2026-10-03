package org.aegiskb.ai.chatagent.rag.model;

import org.aegiskb.ai.chatagent.model.SearchReference;

import java.util.Objects;

public record PromptRenderedSourceEvidence(String identity, SearchReference reference) {

    public PromptRenderedSourceEvidence {
        if (identity == null || identity.isBlank()) {
            throw new IllegalArgumentException("identity must not be blank");
        }
        reference = Objects.requireNonNull(reference, "reference must not be null");
    }
}
