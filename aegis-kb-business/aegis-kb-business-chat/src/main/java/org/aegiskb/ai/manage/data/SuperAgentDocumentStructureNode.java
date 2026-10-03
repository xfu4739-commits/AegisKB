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
@TableName("aegis_kb_document_structure_node")
@EqualsAndHashCode(callSuper = true)
public class SuperAgentDocumentStructureNode extends BaseTableData {

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private Long parseTaskId;

    private Integer nodeNo;

    private Integer nodeType;

    private Long parentNodeId;

    private Long prevSiblingNodeId;

    private Long nextSiblingNodeId;

    private Integer depth;

    private String nodeCode;

    private String title;

    private String anchorText;

    private String canonicalPath;

    private String sectionPath;

    private String contentText;

    private Integer itemIndex;

    private String syntaxSchemaVersion;

    private String syntaxSourceSha256;

    private String syntaxNodeId;

    private String syntaxNodeType;

    private String syntaxSourceOrigin;

    private Integer sourceStartByte;

    private Integer sourceEndByte;

    private Integer sourceStartLine;

    private Integer sourceStartColumn;

    private Integer sourceEndLine;

    private Integer sourceEndColumn;
}
