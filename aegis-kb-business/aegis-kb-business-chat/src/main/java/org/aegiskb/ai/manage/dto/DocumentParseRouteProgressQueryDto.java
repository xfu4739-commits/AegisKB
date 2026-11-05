package org.aegiskb.ai.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 解析与策略推荐进度查询参数
 */
@Data
public class DocumentParseRouteProgressQueryDto {

    @NotNull(message = "文档id不能为空")
    private Long documentId;

    // 可选。传入后优先按这个任务读 Redis；不传则由 Service 找文档下最新 PARSE_ROUTE 任务。
    private Long taskId;

    // 可选。只请求 ID 大于该值的任务日志；null、0 和负数都按“没有游标”处理。
    private Long sinceLogId;

    // 可选。最终会被限制在 1～200；未传或小于等于 0 时使用配置值，配置也缺失则默认 60
    private Integer logLimit;
}
