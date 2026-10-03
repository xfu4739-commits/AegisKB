package org.aegiskb.ai.manage.service;

import org.aegiskb.enums.DocumentFileTypeEnum;
import org.aegiskb.ai.manage.support.DocumentAnalysisResult;

public interface DocumentParserService {

    /**
     * 解析原始文档二进制：委托 Python rag-tools 提取统一文本与块级事实，
     * 并在 Java 侧投影结构节点、表格候选及可持久化解析产物。
     * <p>
     * 由 PARSE_ROUTE 阶段的 {@link org.aegiskb.ai.manage.service.impl.DocumentAsyncProcessServiceImpl}
     * 在从 MinIO 下载原文件后调用；返回的 {@link DocumentAnalysisResult} 供文档画像生成、
     * 结构节点与解析产物持久化使用。
     * <p>
     * Markdown 文件要求解析结果包含 {@code markdown-syntax.v1}；解析文本为空或缺少该契约时抛出
     * {@link IllegalStateException}。
     *
     * @param bytes            原始文件内容字节；允许 {@code null}，按空数组处理
     * @param originalFileName 上传时的原始文件名，用于 rag-tools 路由与产物命名
     * @param mimeType         文件 MIME 类型，传入 rag-tools 解析请求
     * @param fileType         业务文件类型，决定走 Markdown 语法投影或 provider blocks 投影路径
     * @return 含解析文本、统计指标、provider trace、blocks、结构/表格候选及解析产物的分析结果
     */
    DocumentAnalysisResult parse(byte[] bytes, String originalFileName, String mimeType, DocumentFileTypeEnum fileType);
}
