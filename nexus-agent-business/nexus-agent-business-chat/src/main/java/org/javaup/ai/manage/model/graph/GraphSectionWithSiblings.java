package org.javaup.ai.manage.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphSectionWithSiblings {

    private GraphSection section;

    private GraphSection parent;

    private GraphSection previousSibling;

    private GraphSection nextSibling;
}
