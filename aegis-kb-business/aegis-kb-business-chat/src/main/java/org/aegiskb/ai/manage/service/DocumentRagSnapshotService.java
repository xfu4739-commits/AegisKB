package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.dto.DocumentRagPageOverlayDetailQueryDto;
import org.aegiskb.ai.manage.dto.DocumentRagParseWorkbenchQueryDto;
import org.aegiskb.ai.manage.dto.DocumentRagSnapshotQueryDto;
import org.aegiskb.ai.manage.vo.DocumentRagPageOverlayDetailVo;
import org.aegiskb.ai.manage.vo.DocumentRagPageOverlayIndexVo;
import org.aegiskb.ai.manage.vo.DocumentRagParserDiagnosticVo;
import org.aegiskb.ai.manage.vo.DocumentRagSnapshotVo;

public interface DocumentRagSnapshotService {

    DocumentRagSnapshotVo querySnapshot(DocumentRagSnapshotQueryDto dto);

    DocumentRagParserDiagnosticVo queryParserDiagnostic(DocumentRagParseWorkbenchQueryDto dto);

    DocumentRagPageOverlayIndexVo queryPageOverlayIndex(DocumentRagParseWorkbenchQueryDto dto);

    DocumentRagPageOverlayDetailVo queryPageOverlayDetail(DocumentRagPageOverlayDetailQueryDto dto);
}
