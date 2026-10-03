package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.dto.DocumentRagArtifactNodeDetailQueryDto;
import org.aegiskb.ai.manage.dto.DocumentRagArtifactNodePageQueryDto;
import org.aegiskb.ai.manage.dto.DocumentRagArtifactGraphWindowQueryDto;
import org.aegiskb.ai.manage.dto.DocumentRagArtifactRelationPageQueryDto;
import org.aegiskb.ai.manage.dto.DocumentRagArtifactTableWindowQueryDto;
import org.aegiskb.ai.manage.vo.DocumentRagArtifactNodeDetailVo;
import org.aegiskb.ai.manage.vo.DocumentRagArtifactNodePageVo;
import org.aegiskb.ai.manage.vo.DocumentRagArtifactGraphWindowVo;
import org.aegiskb.ai.manage.vo.DocumentRagArtifactRelationPageVo;
import org.aegiskb.ai.manage.vo.DocumentRagArtifactTableWindowVo;

public interface DocumentRagArtifactService {

    DocumentRagArtifactNodePageVo queryNodePage(DocumentRagArtifactNodePageQueryDto dto);

    DocumentRagArtifactNodeDetailVo queryNodeDetail(DocumentRagArtifactNodeDetailQueryDto dto);

    DocumentRagArtifactGraphWindowVo queryGraphWindow(DocumentRagArtifactGraphWindowQueryDto dto);

    DocumentRagArtifactRelationPageVo queryRelationPage(DocumentRagArtifactRelationPageQueryDto dto);

    DocumentRagArtifactTableWindowVo queryTableWindow(DocumentRagArtifactTableWindowQueryDto dto);
}
