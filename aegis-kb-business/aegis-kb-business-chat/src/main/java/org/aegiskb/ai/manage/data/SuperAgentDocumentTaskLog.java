package org.aegiskb.ai.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.aegiskb.database.data.BaseTableData;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("aegis_kb_document_task_log")
@EqualsAndHashCode(callSuper = true)
public class SuperAgentDocumentTaskLog extends BaseTableData {
    /**
     * 任务日志要定义清楚:哪个阶段发生了什么事，严重程度。
     * 
     */
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long taskId;

    private Long documentId;

    private Integer stageType;

    private Integer eventType;

    private Integer logLevel;

    private Integer operatorType;

    private Long operatorId;

    private String content;

    private String detailJson;
}
