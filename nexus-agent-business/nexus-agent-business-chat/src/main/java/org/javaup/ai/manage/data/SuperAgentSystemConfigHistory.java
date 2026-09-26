package org.javaup.ai.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.database.data.BaseTableData;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nexus_agent_system_config_history")
public class SuperAgentSystemConfigHistory extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Integer beforeVersion;
    private Integer afterVersion;
    private String beforeConfigJson;
    private String afterConfigJson;
    private String changeNote;
    private String operatorName;
    private String sourceType;
    private Long restoreFromHistoryId;
}
