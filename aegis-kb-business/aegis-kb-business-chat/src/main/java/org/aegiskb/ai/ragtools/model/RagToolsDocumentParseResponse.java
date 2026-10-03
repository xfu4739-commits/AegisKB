package org.aegiskb.ai.ragtools.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Python rag-tools {@code POST /document/parse} 的 HTTP 响应传输 DTO。
 * <p>
 * 仅用于 Jackson 反序列化与 {@link org.aegiskb.ai.manage.service.impl.RagToolsDocumentParserService}
 * 向 {@link org.aegiskb.ai.manage.support.DocumentAnalysisResult} 投影；不是业务库实体。
 * <p>
 * <b>为何使用 {@code public static} 嵌套类，而不是匿名类、成员内部类或拆成多个顶层类？</b>
 * <ul>
 *   <li>响应 JSON 本身是嵌套结构，需与 Python Pydantic 模型 1:1 对齐。</li>
 *   <li>{@code static} 嵌套类无外部实例依赖，满足 Jackson 无参构造 + 字段/setter 反序列化。</li>
 *   <li>{@code Artifact}/{@code Block}/{@code MarkdownSyntax} 仅服务于本协议，嵌套可避免污染 model 包。</li>
 *   <li>匿名类与非 static 内部类面向行为与外部实例绑定，不适合 JSON DTO；若子结构被多接口复用再拆顶层类。</li>
 * </ul>
 */
@Data
public class RagToolsDocumentParseResponse {

    /** 解析后的统一纯文本，由 Python 从 blocks 渲染或直出。 */
    private String parsedText;

    /** 字符数统计。 */
    private Integer charCount;

    /** Token 数统计（解析器侧估算）。 */
    private Integer tokenCount;

    /** 文档结构层级深度指标。 */
    private Integer structureLevel;

    /** 内容质量等级指标。 */
    private Integer contentQualityLevel;

    /** 标题块数量。 */
    private Integer headingCount;

    /** 段落数量。 */
    private Integer paragraphCount;

    /** 最长段落长度。 */
    private Integer maxParagraphLength;

    /** 可持久化解析产物列表（如 markdown-syntax JSON、图片等），内容为 Base64。 */
    private List<Artifact> artifacts;

    /** 块级解析事实列表；Java 侧据此投影结构节点与表格候选。 */
    private List<Block> blocks;

    /** 实际执行解析的 provider 名称。 */
    private String providerName;

    /** 实际执行解析的 provider 版本。 */
    private String providerVersion;

    /** provider 声明的能力标签列表。 */
    private List<String> capabilities;

    /** Python 侧解析耗时（毫秒）。 */
    private Integer elapsedMs;

    /** 解析过程警告信息。 */
    private List<String> warnings;

    /** 解析失败原因；成功时通常为空。 */
    private String failedReason;

    /** 解析 trace 元数据，供 task.ext_json 与排查使用。 */
    private Map<String, Object> traceMetadata;

    /** Markdown 专用语法契约 {@code markdown-syntax.v1}；非 MD 文件可为 null。 */
    private MarkdownSyntax markdownSyntax;

    /**
     * 解析产物：待上传 MinIO 或入库的附属文件事实。
     */
    @Data
    public static class Artifact {

        /** 产物类型，如 markdown-syntax、image 等。 */
        private String artifactType;

        /** 建议文件名。 */
        private String fileName;

        /** MIME 类型。 */
        private String contentType;

        /** 产物二进制内容的 Base64 编码。 */
        private String contentBase64;

        /** 内容哈希，用于去重与校验。 */
        private String contentHash;

        /** 生成该产物的解析器名称。 */
        private String parserName;

        /** 生成该产物的解析器版本。 */
        private String parserVersion;
    }

    /**
     * 文档块：标题、段落、表格、图片等 typed block 的统一传输形状。
     */
    @Data
    public static class Block {

        /** 块序号，文档内唯一。 */
        private Integer blockNo;

        /** 块类型，如 TITLE、TEXT、TABLE、IMAGE 等。 */
        private String blockType;

        /** 父块序号，构成块树。 */
        private Integer parentBlockNo;

        /** 章节路径，如 {@code 1.2.3}。 */
        private String sectionPath;

        /** 规范化路径，用于定位与溯源。 */
        private String canonicalPath;

        /** 所在页码（PDF 等）。 */
        private Integer pageNo;

        /** 页码范围描述。 */
        private String pageRange;

        /** 版面 bbox JSON（OCR/layout 场景）。 */
        private String bboxJson;

        /** 块纯文本内容。 */
        private String text;

        /** 带权重的内容表示（检索/feature 用）。 */
        private String contentWithWeight;

        /** 表格 HTML 表示。 */
        private String tableHtml;

        /** 表格行数据，外层为行、内层为单元格文本。 */
        private List<List<String>> tableRows;

        /** 图片块关联文件名。 */
        private String imageFileName;

        /** 图片块内联 Base64 内容。 */
        private String imageContentBase64;

        /** 图片说明/题注。 */
        private String imageCaption;

        /** 扩展元数据 JSON 字符串。 */
        private String metadataJson;
    }

    /**
     * Markdown 语法树文档根：契约版本、源文本与节点列表。
     */
    @Data
    public static class MarkdownSyntax {

        /** 契约版本，固定为 {@code markdown-syntax.v1}。 */
        private String schemaVersion;

        /** 语法来源，如 SOURCE_MARKDOWN / PROVIDER_MARKDOWN。 */
        private String sourceOrigin;

        /** 原始 Markdown 源文本。 */
        private String sourceText;

        /** 源文本 UTF-8 字节长度。 */
        private Integer sourceLengthBytes;

        /** 源文本 SHA-256 十六进制摘要。 */
        private String sourceSha256;

        /** 前序遍历的语法节点列表，首节点为 DOCUMENT 根。 */
        private List<MarkdownSyntaxNode> nodes;
    }

    /**
     * Markdown 语法树节点：标题、段落、列表、表格单元等。
     */
    @Data
    public static class MarkdownSyntaxNode {

        /** 前序遍历序号，从 0 连续递增。 */
        private Integer order;

        /** 节点唯一 ID。 */
        private String nodeId;

        /** 父节点 ID；DOCUMENT 根为 null。 */
        private String parentNodeId;

        /** 节点类型，如 HEADING、PARAGRAPH、TABLE_CELL 等。 */
        private String nodeType;

        /** 节点来源，需与文档 sourceOrigin 一致。 */
        private String origin;

        /** 节点在源文本中的字节/行列跨度。 */
        private SourceSpan sourceSpan;

        /** 节点文本内容。 */
        private String text;

        /** 标题层级 1–6，仅 HEADING 有效。 */
        private Integer level;

        /** 列表/标题标记符号，如 {@code #}、{@code 1.}。 */
        private String marker;

        /** 有序列表序号。 */
        private Integer ordinal;

        /** 是否为表头单元格，仅 TABLE_CELL 有效。 */
        private Boolean header;

        /** 单元格对齐方式，仅 TABLE_CELL 有效。 */
        private String alignment;

        /** 表格行索引，仅 TABLE_CELL 有效。 */
        private Integer rowIndex;

        /** 表格列索引，仅 TABLE_CELL 有效。 */
        private Integer columnIndex;

        /** 代码块语言等信息扩展字段。 */
        private String info;
    }

    /**
     * 源文本中的位置跨度，用于语法投影溯源与校验。
     */
    @Data
    public static class SourceSpan {

        /** 起始字节偏移（含）。 */
        private Integer startByte;

        /** 结束字节偏移（含）。 */
        private Integer endByte;

        /** 起始行号，从 1 开始。 */
        private Integer startLine;

        /** 起始列号，从 1 开始。 */
        private Integer startColumn;

        /** 结束行号。 */
        private Integer endLine;

        /** 结束列号。 */
        private Integer endColumn;
    }
}
