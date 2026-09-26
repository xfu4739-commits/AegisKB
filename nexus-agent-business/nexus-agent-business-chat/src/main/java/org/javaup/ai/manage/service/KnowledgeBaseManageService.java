package org.javaup.ai.manage.service;

import org.javaup.ai.manage.data.SuperAgentKnowledgeBase;
import org.javaup.ai.manage.dto.KnowledgeBaseConfigUpdateDto;
import org.javaup.ai.manage.dto.KnowledgeBaseDeleteDto;
import org.javaup.ai.manage.dto.KnowledgeBaseDetailDto;
import org.javaup.ai.manage.dto.KnowledgeBaseSaveDto;
import org.javaup.ai.manage.vo.KnowledgeBaseItemVo;
import org.javaup.ai.manage.vo.KnowledgeBaseOptionVo;

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
     * 按 ID 加载已启用的知识库实体；不存在、已停用或 ID 非法时抛出 {@link org.javaup.exception.SuperAgentFrameException}。
     *
     * @param id 知识库 ID
     * @return 状态为启用的知识库实体
     */
    SuperAgentKnowledgeBase requireEnabled(Long id);
}
