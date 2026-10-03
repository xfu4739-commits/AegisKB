package org.aegiskb.ai.manage.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.aegiskb.ai.auth.support.AdminRequestContext;
import org.aegiskb.ai.manage.dto.SystemConfigHistoryDetailQueryDto;
import org.aegiskb.ai.manage.dto.SystemConfigHistoryPageQueryDto;
import org.aegiskb.ai.manage.dto.SystemConfigHistoryRestoreDto;
import org.aegiskb.ai.manage.dto.SystemConfigItemUpdateDto;
import org.aegiskb.ai.manage.service.SystemConfigService;
import org.aegiskb.ai.manage.vo.SystemConfigCurrentVo;
import org.aegiskb.ai.manage.vo.SystemConfigHistoryItemVo;
import org.aegiskb.ai.manage.vo.SystemConfigHistoryPageVo;
import org.aegiskb.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/manage/config")
public class SystemConfigManageController {

    private final SystemConfigService systemConfigService;

    public SystemConfigManageController(SystemConfigService systemConfigService) {
        this.systemConfigService = systemConfigService;
    }

    @Operation(summary = "查询当前系统配置")
    @PostMapping("/current/query")
    public ApiResponse<SystemConfigCurrentVo> current() {
        return ApiResponse.ok(systemConfigService.current());
    }

    @Operation(summary = "修改单个系统配置项")
    @PostMapping("/item/update")
    public ApiResponse<SystemConfigCurrentVo> updateItem(@Valid @RequestBody SystemConfigItemUpdateDto dto,
                                                         HttpServletRequest request) {
        return ApiResponse.ok(systemConfigService.updateItem(dto, AdminRequestContext.resolveUsername(request)));
    }

    @Operation(summary = "分页查询系统配置历史")
    @PostMapping("/history/page/query")
    public ApiResponse<SystemConfigHistoryPageVo> queryHistory(
        @Valid @RequestBody(required = false) SystemConfigHistoryPageQueryDto dto) {
        return ApiResponse.ok(systemConfigService.queryHistory(dto == null ? new SystemConfigHistoryPageQueryDto() : dto));
    }

    @Operation(summary = "查询系统配置历史详情")
    @PostMapping("/history/detail/query")
    public ApiResponse<SystemConfigHistoryItemVo> queryHistoryDetail(
        @Valid @RequestBody SystemConfigHistoryDetailQueryDto dto) {
        return ApiResponse.ok(systemConfigService.queryHistoryDetail(dto));
    }

    @Operation(summary = "恢复系统配置历史版本")
    @PostMapping("/history/restore")
    public ApiResponse<SystemConfigCurrentVo> restore(@Valid @RequestBody SystemConfigHistoryRestoreDto dto,
                                                      HttpServletRequest request) {
        return ApiResponse.ok(systemConfigService.restore(dto, AdminRequestContext.resolveUsername(request)));
    }
}
