package org.javaup.ai.chatagent.rag.service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.javaup.ai.chatagent.rag.model.RouteScopeAuthorizationMode;
import org.javaup.ai.manage.model.route.KnowledgeRouteDecision;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRoutePlan {

    private KnowledgeRouteDecision routeDecision;

    private RouteScopeAuthorizationMode authorizationMode;

    private String scopeAuthorizationReason;

    private boolean clarificationRequired;

    private String clarificationReply;

    @Builder.Default
    private List<String> clarificationOptions = new ArrayList<>();

    private String clarificationReason;

    private Long recommendedDocumentId;

    private String recommendedDocumentName;

    private Long recommendedTaskId;

    @Builder.Default
    private List<Long> authorizedDocumentIds = new ArrayList<>();

    @Builder.Default
    private List<Long> authorizedTaskIds = new ArrayList<>();
}
