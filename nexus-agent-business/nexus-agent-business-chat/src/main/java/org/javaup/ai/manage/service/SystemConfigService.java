package org.javaup.ai.manage.service;

import org.javaup.ai.manage.dto.SystemConfigHistoryDetailQueryDto;
import org.javaup.ai.manage.dto.SystemConfigHistoryPageQueryDto;
import org.javaup.ai.manage.dto.SystemConfigHistoryRestoreDto;
import org.javaup.ai.manage.dto.SystemConfigItemUpdateDto;
import org.javaup.ai.manage.vo.SystemConfigCurrentVo;
import org.javaup.ai.manage.vo.SystemConfigHistoryItemVo;
import org.javaup.ai.manage.vo.SystemConfigHistoryPageVo;

public interface SystemConfigService {

    SystemConfigCurrentVo current();

    SystemConfigCurrentVo updateItem(SystemConfigItemUpdateDto dto, String operatorName);

    SystemConfigHistoryPageVo queryHistory(SystemConfigHistoryPageQueryDto dto);

    SystemConfigHistoryItemVo queryHistoryDetail(SystemConfigHistoryDetailQueryDto dto);

    SystemConfigCurrentVo restore(SystemConfigHistoryRestoreDto dto, String operatorName);
}
