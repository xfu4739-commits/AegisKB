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
@TableName("aegis_kb_kg_relation_group_member")
@EqualsAndHashCode(callSuper = true)
public class SuperAgentKgRelationGroupMember extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private String scopeKey;

    private Long groupId;

    private String groupKey;

    private Long relationId;

    private Long documentId;

    private Long taskId;

    private Integer evidenceCount;

    private String metadataJson;
}
