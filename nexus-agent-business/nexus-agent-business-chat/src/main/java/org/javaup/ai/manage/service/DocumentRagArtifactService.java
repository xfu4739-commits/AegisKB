package org.javaup.ai.manage.service;

import org.javaup.ai.manage.dto.DocumentRagArtifactNodeDetailQueryDto;
import org.javaup.ai.manage.dto.DocumentRagArtifactNodePageQueryDto;
import org.javaup.ai.manage.dto.DocumentRagArtifactGraphWindowQueryDto;
import org.javaup.ai.manage.dto.DocumentRagArtifactRelationPageQueryDto;
import org.javaup.ai.manage.dto.DocumentRagArtifactTableWindowQueryDto;
import org.javaup.ai.manage.vo.DocumentRagArtifactNodeDetailVo;
import org.javaup.ai.manage.vo.DocumentRagArtifactNodePageVo;
import org.javaup.ai.manage.vo.DocumentRagArtifactGraphWindowVo;
import org.javaup.ai.manage.vo.DocumentRagArtifactRelationPageVo;
import org.javaup.ai.manage.vo.DocumentRagArtifactTableWindowVo;

public interface DocumentRagArtifactService {

    DocumentRagArtifactNodePageVo queryNodePage(DocumentRagArtifactNodePageQueryDto dto);

    DocumentRagArtifactNodeDetailVo queryNodeDetail(DocumentRagArtifactNodeDetailQueryDto dto);

    DocumentRagArtifactGraphWindowVo queryGraphWindow(DocumentRagArtifactGraphWindowQueryDto dto);

    DocumentRagArtifactRelationPageVo queryRelationPage(DocumentRagArtifactRelationPageQueryDto dto);

    DocumentRagArtifactTableWindowVo queryTableWindow(DocumentRagArtifactTableWindowQueryDto dto);
}
