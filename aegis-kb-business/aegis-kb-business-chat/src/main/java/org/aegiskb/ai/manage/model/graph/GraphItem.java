package org.aegiskb.ai.manage.model.graph;

import cn.hutool.core.util.StrUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphItem {

    private Long nodeId;

    private Long documentId;

    private Long parseTaskId;

    private Integer nodeNo;

    private String nodeType;

    private Long sectionNodeId;

    private Long prevSiblingNodeId;

    private Long nextSiblingNodeId;

    private String title;

    private String anchorText;

    private String sectionPath;

    private String canonicalPath;

    private String contentText;

    private Integer itemIndex;

    public String displayText() {
        return StrUtil.blankToDefault(contentText, StrUtil.blankToDefault(anchorText, StrUtil.blankToDefault(title, "")));
    }
}
