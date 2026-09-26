package org.javaup.ai.manage.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DocumentRagSnapshotQueryDto {

    @NotNull(message = "文档id不能为空")
    private Long documentId;

    private Long parseTaskId;

    private Long indexTaskId;

    private Long highlightTableId;

    private Integer highlightTableNo;

    private List<Integer> highlightRowNos;

    private List<String> highlightColumnNames;

    private List<String> highlightCellCoordinates;
}
