package org.javaup.ai.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.javaup.database.data.BaseTableData;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("nexus_agent_system_config")
public class SuperAgentSystemConfig extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Integer configVersion;
    private String configJson;
}
