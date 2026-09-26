package org.javaup.ai.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

import java.util.Date;

/**
 * 文档任务实体，映射表 {@code nexus_agent_document_task}。
 * <p>
 * 记录单次异步长耗时流程（解析路由、索引构建）的执行状态、当前阶段、耗时与错误信息；
 * 由 Kafka 消费后回查并驱动 {@link org.javaup.ai.manage.service.DocumentAsyncProcessService} 执行。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("nexus_agent_document_task")
@EqualsAndHashCode(callSuper = true) // 表示在比较两个对象时还要比较父类的字段
public class SuperAgentDocumentTask extends BaseTableData {

    /** 主键 ID。 */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    /** 所属文档 ID。 */
    private Long documentId;

    /** 执行方案 ID；索引构建任务绑定已确认的策略方案，解析路由任务通常为空。 */
    private Long planId;

    /** 源解析任务 ID；索引构建任务冻结的 PARSE_ROUTE 任务，解析路由任务自身为空。 */
    private Long sourceParseTaskId;

    /** 任务类型：1 解析路由，2 构建索引。 */
    private Integer taskType;

    /** 任务状态：1 新建，2 进行中，3 成功，4 失败，5 已取消。 */
    private Integer taskStatus;

    /** 当前阶段：文件上传、内容解析、策略路由、切块、向量化等，见 DocumentTaskStageEnum。 */
    private Integer currentStage;

    /** 触发来源：1 系统自动，2 用户手动。 */
    private Integer triggerSource;

    /** 执行时策略快照，例如 {@code 1,2,3}。 */
    private String strategySnapshot;

    /** 重试次数。 */
    private Integer retryCount;

    /** 任务开始时间。 */
    private Date startTime;

    /** 任务结束时间。 */
    private Date finishTime;

    /** 任务耗时（毫秒），由结束时间与开始时间差值计算。 */
    private Long costMillis;

    /** 失败时的错误码。 */
    private String errorCode;

    /** 失败时的错误信息。 */
    private String errorMsg;

    /** 扩展信息 JSON。 */
    private String extJson;
}
