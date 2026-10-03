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
@TableName("aegis_kb_document_strategy_step")
@EqualsAndHashCode(callSuper = true)
public class SuperAgentDocumentStrategyStep extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long planId;

    private Long documentId;

    private Integer stepNo;

    private String pipelineType;

    private Integer strategyType;

    private Integer strategyRole;

    private Integer sourceType;

    private Integer executeStatus;

    private String recommendReason;
}
