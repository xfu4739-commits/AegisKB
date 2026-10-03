package org.aegiskb.ai.chatagent.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.aegiskb.ai.manage.data.SuperAgentDocumentStructureNode;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StructureNavigationResult {

    private Long documentId;

    private Long parseTaskId;

    private Long anchorNodeId;

    private SuperAgentDocumentStructureNode current;

    private SuperAgentDocumentStructureNode parent;

    private SuperAgentDocumentStructureNode previousSibling;

    private SuperAgentDocumentStructureNode nextSibling;

    @Builder.Default
    private List<SuperAgentDocumentStructureNode> directChildren = new ArrayList<>();

    private boolean deterministic;

    private String missReason;
}
