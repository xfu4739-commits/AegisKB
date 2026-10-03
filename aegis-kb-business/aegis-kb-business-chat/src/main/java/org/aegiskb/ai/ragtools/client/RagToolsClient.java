package org.aegiskb.ai.ragtools.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.aegiskb.ai.chatagent.support.RestClientFactorySupport;
import org.aegiskb.ai.ragtools.config.RagToolsProperties;
import org.aegiskb.ai.ragtools.model.RagToolsDocumentParseRequest;
import org.aegiskb.ai.ragtools.model.RagToolsDocumentParseResponse;
import org.aegiskb.ai.ragtools.model.RagToolsGraphExtractRequest;
import org.aegiskb.ai.ragtools.model.RagToolsGraphExtractResponse;
import org.aegiskb.ai.ragtools.model.RagToolsHealthResponse;
import org.aegiskb.ai.ragtools.model.RagToolsRaptorBuildRequest;
import org.aegiskb.ai.ragtools.model.RagToolsRaptorBuildResponse;
import org.aegiskb.ai.ragtools.model.RagToolsRerankRequest;
import org.aegiskb.ai.ragtools.model.RagToolsRerankResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class RagToolsClient {
    /**
     * RestClient是同步HTTP客户端，提供流式API通过链式调用来构造请求
     * 它是同步阻塞的不同于响应式的WebClient
     */
    private final RestClient restClient; // 健康检查客户端

    private final RestClient documentParseRestClient; // 文档解析客户端

    private final RestClient graphExtractRestClient; // 图提取客户端

    private final RestClient raptorBuildRestClient; // RAPTOR 构建客户端

    private final ObjectMapper objectMapper;

    public RagToolsClient(RagToolsProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClientFactorySupport.create(
            properties.getBaseUrl(),
            properties.getConnectTimeoutMs(),
            properties.getReadTimeoutMs()
        );
        // OCR 和云任务轮询耗时较长，文档解析不能沿用普通工具接口的短读取超时。
        this.documentParseRestClient = RestClientFactorySupport.create(
            properties.getBaseUrl(),
            properties.getConnectTimeoutMs(),
            properties.getDocumentParseReadTimeoutMs()
        );
        this.graphExtractRestClient = RestClientFactorySupport.create(
            properties.getBaseUrl(),
            properties.getConnectTimeoutMs(),
            properties.getGraphExtractReadTimeoutMs()
        );
        this.raptorBuildRestClient = RestClientFactorySupport.create(
            properties.getBaseUrl(),
            properties.getConnectTimeoutMs(),
            properties.getRaptorBuildReadTimeoutMs()
        );
    }

    public RagToolsHealthResponse health() {
        return restClient.get()
            .uri("/health")
            .retrieve()
            .body(RagToolsHealthResponse.class);
    }

    public RagToolsRerankResponse rerank(RagToolsRerankRequest request) {
        return restClient.post()
            .uri("/rerank")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(RagToolsRerankResponse.class);
    }

    public RagToolsDocumentParseResponse parseDocument(RagToolsDocumentParseRequest request) {
        // 1：记录调用起点，供成功/失败日志统计耗时。
        long startedNanos = System.nanoTime();
        try {
            // 2：同步 POST /document/parse；外层 Kafka 任务虽异步触发，此处会阻塞直到 Python 返回完整解析结果。
            //    使用 documentParseRestClient（长 readTimeout）承载 OCR/云解析等慢路径。
            RagToolsDocumentParseResponse response = documentParseRestClient.post()
                .uri("/document/parse")
                .contentType(MediaType.APPLICATION_JSON) // 定义请求头类型，下面会进行序列化对象
                .body(request) // 请求体：把 Java 对象写成 HTTP Body 发出去
                .retrieve() // 执行请求，拿到响应
                .body(RagToolsDocumentParseResponse.class); // 响应体：把 HTTP 响应 JSON 反序列化成 Java 对象
            // 3：记录解析结果规模与耗时，便于排查慢请求与空响应。
            log.info("Python 文档解析接口调用完成，fileName={}, fileType={}, parsedTextLength={}, blockCount={}, artifactCount={}, costMillis={}",
                request == null ? null : request.getFileName(),
                request == null ? null : request.getFileType(),
                response == null || response.getParsedText() == null ? 0 : response.getParsedText().length(),
                response == null || response.getBlocks() == null ? 0 : response.getBlocks().size(),
                response == null || response.getArtifacts() == null ? 0 : response.getArtifacts().size(),
                elapsedMillis(startedNanos));
            // 4：将 Python 侧统一解析协议交回 Java 投影与持久化链路。
            return response;
        }
        catch (RestClientException exception) {
            // 5：网络/超时/非 2xx 统一包装为 IllegalStateException，由上层 parse route 标记任务失败。
            throw new IllegalStateException("调用 Python 文档解析接口失败，耗时 " + elapsedMillis(startedNanos)
                + "ms: " + exception.getMessage(), exception);
        }
    }

    public RagToolsGraphExtractResponse extractGraph(RagToolsGraphExtractRequest request) {
        return graphExtractRestClient.post()
            .uri("/graph/extract")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(RagToolsGraphExtractResponse.class);
    }

    public RagToolsRaptorBuildResponse buildRaptor(RagToolsRaptorBuildRequest request) {
        long startedNanos = System.nanoTime();
        try {
            RagToolsRaptorBuildResponse response = raptorBuildRestClient.post()
                .uri("/raptor/build")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, httpResponse) -> {
                    String body = StreamUtils.copyToString(httpResponse.getBody(), StandardCharsets.UTF_8);
                    MediaType contentType = httpResponse.getHeaders().getContentType();
                    if (httpResponse.getStatusCode().isError()) {
                        throw new IllegalStateException("Python RAPTOR 构建接口返回错误: status="
                            + httpResponse.getStatusCode().value()
                            + ", contentType=" + contentType
                            + ", body=" + preview(body));
                    }
                    if (body == null || body.isBlank()) {
                        throw new IllegalStateException("Python RAPTOR 构建接口返回空响应: contentType=" + contentType);
                    }
                    try {
                        return objectMapper.readValue(body, RagToolsRaptorBuildResponse.class);
                    }
                    catch (JsonProcessingException exception) {
                        throw new IllegalStateException("Python RAPTOR 构建接口返回非预期 JSON: contentType="
                            + contentType + ", body=" + preview(body), exception);
                    }
                });
            log.info("Python RAPTOR 构建接口调用完成，documentId={}, taskId={}, chunkCount={}, nodeCount={}, llmSummaryEnabled={}, costMillis={}",
                request == null ? null : request.getDocumentId(),
                request == null ? null : request.getTaskId(),
                request == null || request.getChunks() == null ? 0 : request.getChunks().size(),
                response == null || response.getNodes() == null ? 0 : response.getNodes().size(),
                request == null ? null : request.getLlmSummaryEnabled(),
                elapsedMillis(startedNanos));
            return response;
        }
        catch (RuntimeException exception) {
            log.error("Python RAPTOR 构建接口调用失败，documentId={}, taskId={}, chunkCount={}, llmSummaryEnabled={}, costMillis={}, message={}",
                request == null ? null : request.getDocumentId(),
                request == null ? null : request.getTaskId(),
                request == null || request.getChunks() == null ? 0 : request.getChunks().size(),
                request == null ? null : request.getLlmSummaryEnabled(),
                elapsedMillis(startedNanos),
                exception.getMessage(),
                exception);
            if (!(exception instanceof RestClientException)) {
                throw exception;
            }
            throw new IllegalStateException("调用 Python RAPTOR 构建接口失败: " + exception.getMessage(), exception);
        }
    }

    private String preview(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
    }

    private long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
