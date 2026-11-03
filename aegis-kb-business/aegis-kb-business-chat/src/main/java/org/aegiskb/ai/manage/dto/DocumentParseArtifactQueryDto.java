package org.aegiskb.ai.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 *「查询某个文档某次解析版本的产物清单」的请求体。它只定位文档和解析任务，不携带产物内容。
 */
@Data
public class DocumentParseArtifactQueryDto {

    @NotNull(message = "文档id不能为空")
    private Long documentId;

    private Long taskId;
}
