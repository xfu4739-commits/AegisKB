package org.aegiskb.ai.manage.support;

import org.aegiskb.ai.ragtools.model.RagToolsGraphExtractRequest;
import org.aegiskb.ai.ragtools.model.RagToolsGraphExtractResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

public final class GraphRagExtractionContractValidator {

    public static final String SCHEMA_VERSION = "graph-extraction-status.v1";

    public static final String OBSERVATION_WARNINGS_KEY = "contractObservationWarnings";

    private static final Set<String> LAYER_NAMES = Set.of(
        "schema",
        "ner.lightweight",
        "ner.spacy",
        "ner.model"
    );

    public ValidatedContract validate(RagToolsGraphExtractResponse response,
                                      RagToolsGraphExtractRequest request) {
        if (response == null) {
            throw invalid("graph-extraction-status.v1 response is null");
        }
        Map<String, Object> metadata = requiredMap(response.getMetadata(), "metadata");
        Map<String, Object> status = requiredMap(metadata.get("extractionStatus"), "metadata.extractionStatus");
        String schemaVersion = requiredString(status.get("schemaVersion"), "schemaVersion");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw invalid("graph-extraction-status.v1 schemaVersion is required");
        }

        List<RagToolsGraphExtractResponse.Entity> entities = safe(response.getEntities());
        List<RagToolsGraphExtractResponse.Relation> relations = safe(response.getRelations());
        List<RagToolsGraphExtractResponse.Evidence> evidences = safe(response.getEvidences());
        List<RagToolsGraphExtractResponse.Community> communities = safe(response.getCommunities());
        if (!relations.isEmpty() || !communities.isEmpty()) {
            throw invalid("graph-extraction-status.v1 forbids Python relation/community candidates");
        }

        Map<Long, RagToolsGraphExtractRequest.Chunk> chunkById = requestChunks(request);
        int validChunkCount = requiredInt(status.get("validChunkCount"), "validChunkCount");
        if (validChunkCount != chunkById.size()) {
            throw invalid("validChunkCount does not match the controlled Java request");
        }
        requireCount(status, "entityCount", entities.size());
        requireCount(status, "relationCount", relations.size());
        requireCount(status, "evidenceCount", evidences.size());

        validateEntitiesAndEvidence(entities, evidences, chunkById);

        List<String> observationWarnings = new ArrayList<>();
        Map<String, Layer> layers = observe(
            observationWarnings,
            () -> validateLayers(metadata.get("extractorLayers")),
            Map.of()
        );
        observe(observationWarnings, () -> validateCandidateSourceMetadata(entities, evidences, layers));
        observe(observationWarnings, () -> requireSourceCounts(
            metadata.get("extractorSourceCounts"),
            sourceCounts(entities, evidences, "extractorSources"),
            "extractorSourceCounts"
        ));
        observe(observationWarnings, () -> requireSourceCounts(
            metadata.get("candidateSourceCounts"),
            sourceCounts(entities, evidences, "candidateSources"),
            "candidateSourceCounts"
        ));

        String extractionStatus = observe(
            observationWarnings,
            () -> requiredString(status.get("status"), "status"),
            ""
        );
        String entityStatus = observe(
            observationWarnings,
            () -> requiredString(status.get("entityStatus"), "entityStatus"),
            ""
        );
        String relationStatus = observe(
            observationWarnings,
            () -> requiredString(status.get("relationStatus"), "relationStatus"),
            ""
        );
        String relationReason = string(status.get("relationReason"));
        List<String> reasonCodes = observe(
            observationWarnings,
            () -> requiredStringList(status.get("reasonCodes"), "reasonCodes"),
            List.of()
        );
        List<String> expectedReasons = expectedReasons(layers, validChunkCount, entities.size());
        String expectedStatus = expectedStatus(layers, validChunkCount, entities.size());
        observe(observationWarnings, () -> {
            if (!Objects.equals(extractionStatus, expectedStatus)
                || !Objects.equals(entityStatus, entities.isEmpty() ? "empty" : "available")
                || !Objects.equals(relationStatus, "empty")
                || !Objects.equals(relationReason, validChunkCount == 0
                    ? "EMPTY_INPUT" : "NO_GROUNDED_RELATION_CANDIDATE")
                || !Objects.equals(reasonCodes, expectedReasons)) {
                throw invalid("graph-extraction-status.v1 status/reason conservation failed");
            }
        });

        Map<String, Object> validatedMetadata = new LinkedHashMap<>(metadata);
        if (observationWarnings.isEmpty()) {
            validatedMetadata.remove(OBSERVATION_WARNINGS_KEY);
        }
        else {
            validatedMetadata.put(OBSERVATION_WARNINGS_KEY, List.copyOf(observationWarnings));
        }
        return new ValidatedContract(
            extractionStatus,
            reasonCodes,
            validChunkCount,
            entities.size(),
            evidences.size(),
            validatedMetadata,
            List.copyOf(observationWarnings)
        );
    }

    private Map<String, Layer> validateLayers(Object rawLayers) {
        if (!(rawLayers instanceof Collection<?> values)) {
            throw invalid("extractorLayers must contain the four v1 layers");
        }
        Map<String, Layer> result = new LinkedHashMap<>();
        for (Object value : values) {
            Map<String, Object> layer = requiredMap(value, "extractorLayers[]");
            String name = requiredString(layer.get("name"), "extractorLayers.name");
            if (!LAYER_NAMES.contains(name) || result.containsKey(name)) {
                throw invalid("extractorLayers contains a missing, duplicate, or unknown layer");
            }
            boolean enabled = requiredBoolean(layer.get("enabled"), name + ".enabled");
            String status = requiredString(layer.get("status"), name + ".status");
            validateLayerTruthTable(name, enabled, status, layer);
            result.put(name, new Layer(name, enabled, status));
        }
        if (!result.keySet().equals(LAYER_NAMES)) {
            throw invalid("extractorLayers must contain exactly the four v1 layers");
        }
        return result;
    }

    private void validateLayerTruthTable(String name,
                                         boolean enabled,
                                         String status,
                                         Map<String, Object> layer) {
        if ("schema".equals(name)) {
            requireLayer(enabled, status, true, Set.of("ready"), name);
            requireExactSources(layer, List.of("schema.title", "schema.sectionPath", "schema.explicitAlias"), name);
            return;
        }
        if ("ner.lightweight".equals(name)) {
            requireLayer(enabled, status, true, Set.of("ready"), name);
            requireExactSources(layer, List.of("ner.pattern"), name);
            return;
        }
        if ("ner.spacy".equals(name)) {
            requireLayer(enabled, status, true, Set.of("loaded", "not_used", "unavailable"), name);
            if ("loaded".equals(status)) {
                requiredString(layer.get("modelName"), name + ".modelName");
            }
            else {
                requiredString(layer.get("reason"), name + ".reason");
            }
            return;
        }
        requiredString(layer.get("provider"), name + ".provider");
        if (!layer.containsKey("modelName") || !(layer.get("modelName") instanceof String)) {
            throw invalid(name + ".modelName is required");
        }
        boolean loaded = requiredBoolean(layer.get("loaded"), name + ".loaded");
        if (!enabled) {
            requireLayer(false, status, false, Set.of("disabled"), name);
            if (loaded) {
                throw invalid(name + " disabled layer cannot be loaded");
            }
            return;
        }
        requireLayer(true, status, true, Set.of("loaded", "unavailable"), name);
        if ("loaded".equals(status) != loaded) {
            throw invalid(name + " loaded flag contradicts status");
        }
        if ("loaded".equals(status)) {
            requiredString(layer.get("modelName"), name + ".modelName");
        }
        else {
            requiredString(layer.get("reason"), name + ".reason");
        }
    }

    private void requireLayer(boolean actualEnabled,
                              String actualStatus,
                              boolean expectedEnabled,
                              Set<String> allowedStatuses,
                              String name) {
        if (actualEnabled != expectedEnabled || !allowedStatuses.contains(actualStatus)) {
            throw invalid(name + " enabled/status combination is invalid");
        }
    }

    private void requireExactSources(Map<String, Object> layer, List<String> expected, String name) {
        if (!Objects.equals(requiredStringList(layer.get("sources"), name + ".sources"), expected)) {
            throw invalid(name + " sources do not match graph-extraction-status.v1");
        }
    }

    private void validateEntitiesAndEvidence(List<RagToolsGraphExtractResponse.Entity> entities,
                                             List<RagToolsGraphExtractResponse.Evidence> evidences,
                                             Map<Long, RagToolsGraphExtractRequest.Chunk> chunkById) {
        Map<String, RagToolsGraphExtractResponse.Entity> entityById = new LinkedHashMap<>();
        for (RagToolsGraphExtractResponse.Entity entity : entities) {
            if (entity == null) {
                throw invalid("entity cannot be null");
            }
            String id = requiredString(entity.getId(), "entity.id");
            requiredString(entity.getName(), "entity.name");
            if (entityById.putIfAbsent(id, entity) != null) {
                throw invalid("duplicate entity id");
            }
            requireConfidence(entity.getConfidence(), "entity.confidence");
            List<Long> sourceChunkIds = requiredLongList(entity.getSourceChunkIds(), "entity.sourceChunkIds");
            if (sourceChunkIds.isEmpty() || !chunkById.keySet().containsAll(sourceChunkIds)) {
                throw invalid("entity source chunk lineage is invalid");
            }
            List<String> evidenceIds = requiredStringList(entity.getEvidenceIds(), "entity.evidenceIds");
            if (evidenceIds.isEmpty()) {
                throw invalid("entity must reference grounded evidence");
            }
        }

        Map<String, RagToolsGraphExtractResponse.Evidence> evidenceById = new LinkedHashMap<>();
        for (RagToolsGraphExtractResponse.Evidence evidence : evidences) {
            if (evidence == null) {
                throw invalid("evidence cannot be null");
            }
            String id = requiredString(evidence.getId(), "evidence.id");
            if (evidenceById.putIfAbsent(id, evidence) != null) {
                throw invalid("duplicate evidence id");
            }
            String entityId = requiredString(evidence.getEntityId(), "evidence.entityId");
            if (!entityById.containsKey(entityId) || !string(evidence.getRelationId()).isEmpty()) {
                throw invalid("Python v1 evidence owner is invalid");
            }
            RagToolsGraphExtractRequest.Chunk chunk = chunkById.get(evidence.getChunkId());
            String quote = requiredString(evidence.getQuoteText(), "evidence.quoteText");
            if (chunk == null || !normalize(chunk.getText()).contains(normalize(quote))) {
                throw invalid("evidence quote/chunk lineage is invalid");
            }
            Map<String, Object> evidenceMetadata = requiredMap(evidence.getMetadata(), "evidence.metadata");
            if (!Boolean.TRUE.equals(evidenceMetadata.get("grounded"))) {
                throw invalid("evidence grounded flag is required");
            }
        }

        Set<String> referencedEvidenceIds = new LinkedHashSet<>();
        for (RagToolsGraphExtractResponse.Entity entity : entities) {
            Set<Long> entityChunkIds = new LinkedHashSet<>(requiredLongList(entity.getSourceChunkIds(), "entity.sourceChunkIds"));
            for (String evidenceId : requiredStringList(entity.getEvidenceIds(), "entity.evidenceIds")) {
                RagToolsGraphExtractResponse.Evidence evidence = evidenceById.get(evidenceId);
                if (evidence == null || !Objects.equals(entity.getId(), evidence.getEntityId())
                    || !entityChunkIds.contains(evidence.getChunkId())) {
                    throw invalid("entity/evidence owner and chunk conservation failed");
                }
                referencedEvidenceIds.add(evidenceId);
            }
        }
        if (!referencedEvidenceIds.equals(evidenceById.keySet())) {
            throw invalid("orphan evidence is forbidden");
        }
    }

    private void validateCandidateSourceMetadata(List<RagToolsGraphExtractResponse.Entity> entities,
                                                 List<RagToolsGraphExtractResponse.Evidence> evidences,
                                                 Map<String, Layer> layers) {
        for (Map<String, Object> metadata : itemMetadata(entities, evidences)) {
            validateItemSources(metadata, layers);
        }
    }

    private void validateItemSources(Map<String, Object> metadata, Map<String, Layer> layers) {
        Map<String, Object> itemMetadata = requiredMap(metadata, "candidate metadata");
        List<String> extractorSources = requiredStringList(itemMetadata.get("extractorSources"), "extractorSources");
        List<String> candidateSources = requiredStringList(itemMetadata.get("candidateSources"), "candidateSources");
        if (extractorSources.isEmpty() || candidateSources.isEmpty()) {
            throw invalid("candidate source metadata is required");
        }
        for (String extractorSource : extractorSources) {
            if (!Set.of("schema", "ner").contains(extractorSource)) {
                throw invalid("unknown extractor source");
            }
        }
        for (String candidateSource : candidateSources) {
            String layerName = sourceLayer(candidateSource);
            Layer layer = layers.get(layerName);
            if (layer == null || !layer.canProduce()) {
                throw invalid("candidate source contradicts extractor layer status");
            }
        }
    }

    private String sourceLayer(String candidateSource) {
        if (candidateSource == null || !candidateSource.matches("[A-Za-z0-9]+(?:[._-][A-Za-z0-9]+)+")) {
            throw invalid("candidate source key grammar is invalid");
        }
        if (candidateSource.startsWith("schema.")) {
            return "schema";
        }
        if (candidateSource.equals("ner.pattern") || candidateSource.startsWith("ner.pattern.")) {
            return "ner.lightweight";
        }
        if (candidateSource.startsWith("ner.spacy.")) {
            return "ner.spacy";
        }
        if (candidateSource.startsWith("ner.model.")) {
            return "ner.model";
        }
        throw invalid("candidate source has no v1 layer owner");
    }

    private Map<String, Integer> sourceCounts(List<RagToolsGraphExtractResponse.Entity> entities,
                                              List<RagToolsGraphExtractResponse.Evidence> evidences,
                                              String key) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map<String, Object> metadata : itemMetadata(entities, evidences)) {
            List<String> values = requiredStringList(metadata.get(key), key);
            for (String value : values) {
                if ("candidateSources".equals(key)) {
                    sourceLayer(value);
                }
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts;
    }

    private List<Map<String, Object>> itemMetadata(List<RagToolsGraphExtractResponse.Entity> entities,
                                                   List<RagToolsGraphExtractResponse.Evidence> evidences) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (RagToolsGraphExtractResponse.Entity entity : entities) {
            result.add(requiredMap(entity.getMetadata(), "entity.metadata"));
        }
        for (RagToolsGraphExtractResponse.Evidence evidence : evidences) {
            result.add(requiredMap(evidence.getMetadata(), "evidence.metadata"));
        }
        return result;
    }

    private void requireSourceCounts(Object rawCounts, Map<String, Integer> expected, String field) {
        Map<String, Object> counts = requiredMap(rawCounts, field);
        Map<String, Integer> actual = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : counts.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                throw invalid(field + " key grammar is invalid");
            }
            int count = requiredInt(entry.getValue(), field + "." + entry.getKey());
            if (count <= 0) {
                throw invalid(field + " values must be positive");
            }
            actual.put(entry.getKey(), count);
        }
        if (!actual.equals(expected)) {
            throw invalid(field + " does not conserve candidate metadata");
        }
    }

    private List<String> expectedReasons(Map<String, Layer> layers, int validChunkCount, int entityCount) {
        List<String> reasons = new ArrayList<>();
        if (unavailable(layers, "ner.spacy")) {
            reasons.add("SPACY_MODEL_UNAVAILABLE");
        }
        if (unavailable(layers, "ner.model")) {
            reasons.add("NER_MODEL_UNAVAILABLE");
        }
        if (validChunkCount == 0) {
            reasons.add("EMPTY_INPUT");
        }
        else if (entityCount == 0) {
            reasons.add("NO_GROUNDED_ENTITY_CANDIDATE");
        }
        if (validChunkCount > 0) {
            reasons.add("NO_GROUNDED_RELATION_CANDIDATE");
        }
        return reasons;
    }

    private boolean unavailable(Map<String, Layer> layers, String name) {
        Layer layer = layers.get(name);
        return layer != null && layer.unavailable();
    }

    private String expectedStatus(Map<String, Layer> layers, int validChunkCount, int entityCount) {
        if (validChunkCount == 0 || entityCount == 0) {
            return "empty";
        }
        return layers.values().stream().anyMatch(Layer::unavailable)
            ? "completed_with_degradation" : "completed";
    }

    private Map<Long, RagToolsGraphExtractRequest.Chunk> requestChunks(RagToolsGraphExtractRequest request) {
        Map<Long, RagToolsGraphExtractRequest.Chunk> result = new LinkedHashMap<>();
        if (request == null || request.getChunks() == null) {
            return result;
        }
        for (RagToolsGraphExtractRequest.Chunk chunk : request.getChunks()) {
            if (chunk == null || chunk.getChunkId() == null || string(chunk.getText()).isBlank()
                || result.putIfAbsent(chunk.getChunkId(), chunk) != null) {
                throw invalid("controlled request chunk lineage is invalid");
            }
        }
        return result;
    }

    private void requireCount(Map<String, Object> status, String field, int expected) {
        if (requiredInt(status.get(field), field) != expected) {
            throw invalid(field + " does not match the response list");
        }
    }

    private void requireConfidence(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw invalid(field + " must be numeric");
        }
        double confidence = number.doubleValue();
        if (!Double.isFinite(confidence) || confidence < 0D || confidence > 1D) {
            throw invalid(field + " is outside [0,1]");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requiredMap(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalid(field + " is required");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                throw invalid(field + " keys must be strings");
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private String requiredString(Object value, String field) {
        String result = string(value);
        if (result.isBlank()) {
            throw invalid(field + " is required");
        }
        return result;
    }

    private String string(Object value) {
        return value instanceof String text ? text.trim() : "";
    }

    private int requiredInt(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw invalid(field + " must be an integer");
        }
        double raw = number.doubleValue();
        int result = number.intValue();
        if (!Double.isFinite(raw) || raw != result || result < 0) {
            throw invalid(field + " must be a non-negative integer");
        }
        return result;
    }

    private boolean requiredBoolean(Object value, String field) {
        if (!(value instanceof Boolean result)) {
            throw invalid(field + " must be boolean");
        }
        return result;
    }

    private List<String> requiredStringList(Object value, String field) {
        if (!(value instanceof Collection<?> collection)) {
            throw invalid(field + " must be a list");
        }
        List<String> result = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (Object item : collection) {
            String text = requiredString(item, field + "[]");
            if (!unique.add(text)) {
                throw invalid(field + " contains duplicates");
            }
            result.add(text);
        }
        return result;
    }

    private List<Long> requiredLongList(Object value, String field) {
        if (!(value instanceof Collection<?> collection)) {
            throw invalid(field + " must be a list");
        }
        List<Long> result = new ArrayList<>();
        Set<Long> unique = new LinkedHashSet<>();
        for (Object item : collection) {
            if (!(item instanceof Number number) || number.longValue() <= 0 || !unique.add(number.longValue())) {
                throw invalid(field + " contains an invalid or duplicate id");
            }
            result.add(number.longValue());
        }
        return result;
    }

    private String normalize(String text) {
        return string(text).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    private void observe(List<String> warnings, Runnable validation) {
        try {
            validation.run();
        }
        catch (ContractException exception) {
            warnings.add(observationDetail(exception));
        }
    }

    private <T> T observe(List<String> warnings, Supplier<T> validation, T fallback) {
        try {
            return validation.get();
        }
        catch (ContractException exception) {
            warnings.add(observationDetail(exception));
            return fallback;
        }
    }

    private String observationDetail(ContractException exception) {
        String message = exception.getMessage();
        String prefix = "FAILED_CONTRACT[" + SCHEMA_VERSION + "]: ";
        return message != null && message.startsWith(prefix) ? message.substring(prefix.length()) : String.valueOf(message);
    }

    private ContractException invalid(String detail) {
        return new ContractException("FAILED_CONTRACT[" + SCHEMA_VERSION + "]: " + detail);
    }

    public record ValidatedContract(String status,
                                    List<String> reasonCodes,
                                    int validChunkCount,
                                    int entityCount,
                                    int evidenceCount,
                                    Map<String, Object> metadata,
                                    List<String> observationWarnings) {

        public boolean degraded() {
            return reportedDegraded() || observationDegraded();
        }

        public boolean reportedDegraded() {
            return "completed_with_degradation".equals(status);
        }

        public boolean observationDegraded() {
            return observationWarnings != null && !observationWarnings.isEmpty();
        }

        public boolean empty() {
            return entityCount == 0;
        }
    }

    public static final class ContractException extends IllegalArgumentException {

        private ContractException(String message) {
            super(message);
        }
    }

    private record Layer(String name, boolean enabled, String status) {

        private boolean unavailable() {
            return enabled && "unavailable".equals(status);
        }

        private boolean canProduce() {
            return enabled && Set.of("ready", "loaded").contains(status);
        }
    }
}
