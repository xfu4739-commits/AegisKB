package org.javaup.ai.manage.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.javaup.database.data.BaseTableData;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("nexus_agent_knowledge_scope_node")
@EqualsAndHashCode(callSuper = true)
public class SuperAgentKnowledgeScopeNode extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long knowledgeBaseId;

    private String scopeName;

    private Long parentScopeId;

    private String description;

    private String aliases;

    private String examples;

    private Integer sortOrder;
}
