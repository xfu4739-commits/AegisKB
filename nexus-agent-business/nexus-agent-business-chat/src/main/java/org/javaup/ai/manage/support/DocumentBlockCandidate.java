package org.javaup.ai.manage.support;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DocumentBlockCandidate {

    private Integer blockNo;

    private String blockType;

    private Integer parentBlockNo;

    private String sectionPath;

    private String canonicalPath;

    private Integer pageNo;

    private String pageRange;

    private String bboxJson;

    private String text;

    private String contentWithWeight;

    private String tableHtml;

    private List<List<String>> tableRows = new ArrayList<>();

    private String imageFileName;

    private String imageContentBase64;

    private String imageCaption;

    private String metadataJson;
}
