package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.dto.DocumentProfileBatchRegenerateDto;
import org.aegiskb.ai.manage.dto.DocumentProfileDetailQueryDto;
import org.aegiskb.ai.manage.dto.DocumentProfileRegenerateDto;
import org.aegiskb.ai.manage.dto.KnowledgeRouteTraceQueryDto;
import org.aegiskb.ai.manage.dto.KnowledgeScopeDeleteDto;
import org.aegiskb.ai.manage.dto.KnowledgeScopeQueryDto;
import org.aegiskb.ai.manage.dto.KnowledgeScopeSaveDto;
import org.aegiskb.ai.manage.dto.KnowledgeTopicDeleteDto;
import org.aegiskb.ai.manage.dto.KnowledgeTopicQueryDto;
import org.aegiskb.ai.manage.dto.KnowledgeTopicSaveDto;
import org.aegiskb.ai.manage.dto.TopicDocumentRelationListQueryDto;
import org.aegiskb.ai.manage.dto.TopicDocumentRelationRemoveDto;
import org.aegiskb.ai.manage.dto.TopicDocumentRelationSaveDto;
import org.aegiskb.ai.manage.vo.DocumentProfileVo;
import org.aegiskb.ai.manage.vo.KnowledgeRouteTracePageVo;
import org.aegiskb.ai.manage.vo.KnowledgeScopeItemVo;
import org.aegiskb.ai.manage.vo.KnowledgeTopicItemVo;
import org.aegiskb.ai.manage.vo.TopicDocumentRelationItemVo;

import java.util.List;

public interface KnowledgeManageService {

    KnowledgeScopeItemVo saveScope(KnowledgeScopeSaveDto dto);

    boolean deleteScope(KnowledgeScopeDeleteDto dto);

    List<KnowledgeScopeItemVo> listScopes(KnowledgeScopeQueryDto dto);

    KnowledgeTopicItemVo saveTopic(KnowledgeTopicSaveDto dto);

    boolean deleteTopic(KnowledgeTopicDeleteDto dto);

    List<KnowledgeTopicItemVo> listTopics(KnowledgeTopicQueryDto dto);

    DocumentProfileVo queryProfile(DocumentProfileDetailQueryDto dto);

    DocumentProfileVo regenerateProfile(DocumentProfileRegenerateDto dto);

    List<DocumentProfileVo> batchRegenerateProfiles(DocumentProfileBatchRegenerateDto dto);

    List<TopicDocumentRelationItemVo> listTopicDocuments(TopicDocumentRelationListQueryDto dto);

    TopicDocumentRelationItemVo saveTopicDocumentRelation(TopicDocumentRelationSaveDto dto);

    boolean removeTopicDocumentRelation(TopicDocumentRelationRemoveDto dto);

    KnowledgeRouteTracePageVo queryRouteTracePage(KnowledgeRouteTraceQueryDto dto);
}
