package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.data.SuperAgentKnowledgeBase;
import org.aegiskb.ai.manage.dto.KnowledgeBaseConfigUpdateDto;
import org.aegiskb.ai.manage.dto.KnowledgeBaseDeleteDto;
import org.aegiskb.ai.manage.dto.KnowledgeBaseDetailDto;
import org.aegiskb.ai.manage.dto.KnowledgeBaseSaveDto;
import org.aegiskb.ai.manage.vo.KnowledgeBaseItemVo;
import org.aegiskb.ai.manage.vo.KnowledgeBaseOptionVo;

import java.util.Collection;
import java.util.List;

public interface KnowledgeBaseManageService {

    KnowledgeBaseItemVo save(KnowledgeBaseSaveDto dto);

    boolean delete(KnowledgeBaseDeleteDto dto);

    List<KnowledgeBaseItemVo> list();

    KnowledgeBaseItemVo detail(KnowledgeBaseDetailDto dto);

    KnowledgeBaseItemVo updateConfig(KnowledgeBaseConfigUpdateDto dto);

    List<KnowledgeBaseOptionVo> listOptions();

    List<SuperAgentKnowledgeBase> listEnabledByIds(Collection<Long> ids);

    List<SuperAgentKnowledgeBase> listAllEnabled();

    /**
     * 按 ID 加载已启用的知识库实体；不存在、已停用或 ID 非法时抛出 {@link org.aegiskb.exception.SuperAgentFrameException}。
     *
     * @param id 知识库 ID
     * @return 状态为启用的知识库实体
     */
    SuperAgentKnowledgeBase requireEnabled(Long id);
}
