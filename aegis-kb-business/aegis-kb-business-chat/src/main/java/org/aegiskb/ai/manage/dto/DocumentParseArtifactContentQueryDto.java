package org.aegiskb.ai.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 定位一份已经解析出来的产物，并读取它的正文。它同时给预览和下载两个接口用。
 */
@Data
public class DocumentParseArtifactContentQueryDto {

    @NotNull(message = "文档id不能为空")
    private Long documentId;

    private Long taskId;

    @NotNull(message = "解析产物id不能为空")
    private Long artifactId;
}
