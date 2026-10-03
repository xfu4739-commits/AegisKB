package org.aegiskb.ai.manage.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChunkCandidate {

    private String sectionPath;

    private Long structureNodeId;

    private Integer structureNodeType;

    private String canonicalPath;

    private Integer itemIndex;

    private String text;

    private String contentWithWeight;

    private String chunkType;

    private String title;

    private String keywords;

    private String questions;

    private Integer sourceType;

    private Integer pageNo;

    private String pageRange;

    private String bboxJson;

    private String sourceBlockIds;

    public ChunkCandidate(String sectionPath,
                          Long structureNodeId,
                          Integer structureNodeType,
                          String canonicalPath,
                          Integer itemIndex,
                          String text,
                          Integer sourceType) {
        this(sectionPath, structureNodeId, structureNodeType, canonicalPath, itemIndex, text, null, null, null, null, null,
            sourceType, null, null, null, null);
    }

    public ChunkCandidate(String sectionPath, String text, Integer sourceType) {
        this(sectionPath, null, null, "", null, text, null, null, null, null, null,
            sourceType, null, null, null, null);
    }
}
