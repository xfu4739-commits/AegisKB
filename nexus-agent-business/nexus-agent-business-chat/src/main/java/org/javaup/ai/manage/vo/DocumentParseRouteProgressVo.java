package org.javaup.ai.manage.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 解析路由（PARSE_ROUTE）进度聚合视图，供 {@code queryParseRouteProgress} 轮询与前端进度展示。
 * <p>
 * 由 {@link org.javaup.ai.manage.service.impl.DocumentParseRouteProgressAssembler} 从 document、task、plan、steps
 * 与任务日志组装；Redis 热缓存与接口响应均使用本 VO。MySQL 中的 document / task / task_log 仍是长期事实来源。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseRouteProgressVo {

    /** 文档 ID。 */
    private Long documentId;

    /** PARSE_ROUTE 任务 ID。 */
    private Long taskId;

    /** 任务类型码，见 {@link org.javaup.enums.DocumentTaskTypeEnum}。 */
    private Integer taskType;

    /** 任务类型中文名。 */
    private String taskTypeName;

    /** 任务状态码，见 {@link org.javaup.enums.DocumentTaskStatusEnum}。 */
    private Integer taskStatus;

    /** 任务状态中文名。 */
    private String taskStatusName;

    /** 当前阶段码，见 {@link org.javaup.enums.DocumentTaskStageEnum}。 */
    private Integer currentStage;

    /** 当前阶段中文名。 */
    private String currentStageName;

    /** 当前阶段枚举名（如 {@code CONTENT_PARSE}），供前端稳定绑定。 */
    private String stageCode;

    /** 当前阶段展示标签（可能比 stageName 更贴近 UI 文案）。 */
    private String stageLabel;

    /** 文档解析状态码，见 {@link org.javaup.enums.DocumentParseStatusEnum}。 */
    private Integer parseStatus;

    /** 文档解析状态中文名。 */
    private String parseStatusName;

    /** 策略推荐状态码，见 {@link org.javaup.enums.DocumentStrategyStatusEnum}。 */
    private Integer strategyStatus;

    /** 策略推荐状态中文名。 */
    private String strategyStatusName;

    /** 解析方式描述（如按文件类型确定的解析模式）。 */
    private String parseMode;

    /** Python 解析器提供方名称。 */
    private String parserName;

    /** Python 解析器版本。 */
    private String parserVersion;

    /** 远端解析任务 jobId（来自 parserTraceMetadata）。 */
    private String jobId;

    /** 解析得到的页数。 */
    private Integer pageCount;

    /** 解析得到的块（block）数量。 */
    private Integer blockCount;

    /** 解析得到的表格数量。 */
    private Integer tableCount;

    /** 解析得到的图片数量。 */
    private Integer figureCount;

    /** 解析得到的图注数量。 */
    private Integer captionCount;

    /** 是否已有可确认的策略方案（{@code planId != null}）。 */
    private Boolean planReady;

    /** 当前策略方案 ID；无方案时为 null。 */
    private Long planId;

    /** 策略推荐原因说明。 */
    private String recommendReason;

    /** 父块流水线步骤数。 */
    private Integer parentStepCount;

    /** 子块流水线步骤数。 */
    private Integer childStepCount;

    /** 任务开始时间。 */
    private Date startTime;

    /** 任务结束时间；进行中时为 null。 */
    private Date finishTime;

    /** 任务耗时（毫秒），由结束时间与开始时间差值计算。 */
    private Long costMillis;

    /** 已运行时长（毫秒）；进行中按当前时间推算，已结束同 {@link #costMillis}。 */
    private Long elapsedMillis;

    /** 快照中最新一条任务日志 ID，供前端增量拉取。 */
    private Long latestLogId;

    /** 累计任务日志总条数（可能大于快照内 {@link #logs} 实际携带条数）。 */
    private Long totalLogCount;

    /** 快照内携带的最近任务日志列表（Redis 侧最多约 80 条）。 */
    private List<DocumentTaskLogVo> logs;

    /** 失败时的错误码。 */
    private String errorCode;

    /** 失败时的错误信息。 */
    private String errorMsg;

    /** 任务是否处于进行中（新建或运行中）。 */
    private Boolean running;
}
