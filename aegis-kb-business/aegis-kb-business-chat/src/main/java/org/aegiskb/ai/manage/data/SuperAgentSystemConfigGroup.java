package org.aegiskb.ai.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.aegiskb.database.data.BaseTableData;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aegis_kb_system_config_group")
public class SuperAgentSystemConfigGroup extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private String groupKey;
    private String groupLabel;
    private String groupDescription;
    private Integer sortOrder;
}
