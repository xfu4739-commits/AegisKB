package org.aegiskb.ai.manage.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.aegiskb.ai.manage.dto.QualityOverviewQueryDto;
import org.aegiskb.ai.manage.service.QualityOverviewService;
import org.aegiskb.ai.manage.vo.QualityOverviewVo;
import org.aegiskb.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage/observability")
public class ObservabilityManageController {

    private final QualityOverviewService qualityOverviewService;

    public ObservabilityManageController(QualityOverviewService qualityOverviewService) {
        this.qualityOverviewService = qualityOverviewService;
    }

    @Operation(summary = "查询知识运行全景")
    @PostMapping("/quality/overview/query")
    public ApiResponse<QualityOverviewVo> queryQualityOverview(
        @RequestBody(required = false) QualityOverviewQueryDto dto) {
        return ApiResponse.ok(qualityOverviewService.queryQualityOverview(
            dto == null ? new QualityOverviewQueryDto() : dto));
    }
}
