package org.aegiskb.ai.chatagent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TavilySearchRequest {

    private String query;
    private String topic;
    private Integer maxResults;
}
