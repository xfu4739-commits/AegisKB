package org.javaup.ai.manage.service;

import org.javaup.ai.manage.dto.DocumentRagPageOverlayDetailQueryDto;
import org.javaup.ai.manage.dto.DocumentRagParseWorkbenchQueryDto;
import org.javaup.ai.manage.dto.DocumentRagSnapshotQueryDto;
import org.javaup.ai.manage.vo.DocumentRagPageOverlayDetailVo;
import org.javaup.ai.manage.vo.DocumentRagPageOverlayIndexVo;
import org.javaup.ai.manage.vo.DocumentRagParserDiagnosticVo;
import org.javaup.ai.manage.vo.DocumentRagSnapshotVo;

public interface DocumentRagSnapshotService {

    DocumentRagSnapshotVo querySnapshot(DocumentRagSnapshotQueryDto dto);

    DocumentRagParserDiagnosticVo queryParserDiagnostic(DocumentRagParseWorkbenchQueryDto dto);

    DocumentRagPageOverlayIndexVo queryPageOverlayIndex(DocumentRagParseWorkbenchQueryDto dto);

    DocumentRagPageOverlayDetailVo queryPageOverlayDetail(DocumentRagPageOverlayDetailQueryDto dto);
}
