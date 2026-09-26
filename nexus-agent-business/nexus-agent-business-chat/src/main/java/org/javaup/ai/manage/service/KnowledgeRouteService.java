package org.javaup.ai.manage.service;

import org.javaup.ai.manage.model.route.KnowledgeRouteContext;
import org.javaup.ai.manage.model.route.KnowledgeRouteDecision;

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
