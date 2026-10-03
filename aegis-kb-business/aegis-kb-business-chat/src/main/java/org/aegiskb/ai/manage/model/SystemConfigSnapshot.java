package org.aegiskb.ai.manage.model;

import lombok.Data;
import org.aegiskb.ai.chatagent.config.ChatAgentProperties;
import org.aegiskb.ai.chatagent.rag.config.ChatRagProperties;
import org.aegiskb.ai.chatagent.rag.model.RagRuntimeOptions;
import org.aegiskb.ai.manage.config.DocumentManageProperties;
import org.aegiskb.ai.manage.config.GraphRagBuildProperties;

/**
 * Complete, typed system configuration snapshot persisted in aegis_kb_system_config.
 */
@Data
public class SystemConfigSnapshot {

    private RagRuntimeOptions ragRuntime = RagRuntimeOptions.defaults();
    private ChatAgentProperties chat = new ChatAgentProperties();
    private ChatRagProperties rag = new ChatRagProperties();
    private GraphRagBuildProperties graphRagBuild = new GraphRagBuildProperties();
    private GraphRagExtractionOptions graphRagExtraction = new GraphRagExtractionOptions();
    private ToggleOptions graphRagCommunityReport = new ToggleOptions();
    private ToggleOptions graphRagEntityResolution = new ToggleOptions();
    private ChunkEnrichmentOptions chunkEnrichment = new ChunkEnrichmentOptions();
    private DocumentManageProperties.IndexBuild indexBuild = new DocumentManageProperties.IndexBuild();
    private DocumentManageProperties.Chunk chunk = new DocumentManageProperties.Chunk();

    public static SystemConfigSnapshot defaults() {
        SystemConfigSnapshot snapshot = new SystemConfigSnapshot();

        ChatRagProperties rag = snapshot.getRag();
        rag.getRewriteOptions().setTemperature(0.2D);
        rag.setNoEvidenceReply("当前没有从当前文档中检索到足够证据，暂时不能给出可靠结论。你可以补充更具体的章节名、标题或关键词后再试。");

        snapshot.getGraphRagBuild().setLeaseTtlSeconds(6000);
        snapshot.getIndexBuild().setEmbeddingBatchSize(5);
        snapshot.getIndexBuild().setElasticsearchRefreshWait(Boolean.TRUE);
        return snapshot;
    }

    @Data
    public static class ToggleOptions {
        private boolean enabled = true;
    }

    @Data
    public static class GraphRagExtractionOptions {
        private boolean enabled = true;
        private int batchChunkLimit = 3;
        private int batchTokenLimit = 2800;
        private int maxEntitiesPerCall = 8;
        private int maxRelationsPerCall = 6;
        private int maxEvidencesPerCall = 8;
        private int maxQuoteChars = 180;
        private int maxReasonChars = 240;
        private int maxUnitTextChars = 420;
    }

    @Data
    public static class ChunkEnrichmentOptions {
        private boolean enabled = true;
        private int maxKeywords = 8;
        private int maxQuestions = 4;
        private int maxKeywordChars = 32;
        private int maxQuestionChars = 40;
        private int batchChunkLimit = 6;
        private int batchTokenLimit = 2400;
    }
}
