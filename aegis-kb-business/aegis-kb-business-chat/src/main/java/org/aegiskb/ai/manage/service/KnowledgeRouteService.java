package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.model.route.KnowledgeRouteContext;
import org.aegiskb.ai.manage.model.route.KnowledgeRouteDecision;

public interface KnowledgeRouteService {

    KnowledgeRouteDecision route(KnowledgeRouteContext context);

    void recordShadowRoute(String conversationId,
                           long exchangeId,
                           Long selectedDocumentId,
                           KnowledgeRouteContext context);

    void recordAutoRoute(String conversationId,
                         long exchangeId,
                         KnowledgeRouteContext context,
                         KnowledgeRouteDecision decision,
                         Long hardScopedDocumentId);
}
