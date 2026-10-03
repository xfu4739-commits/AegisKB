package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.dto.SystemConfigHistoryDetailQueryDto;
import org.aegiskb.ai.manage.dto.SystemConfigHistoryPageQueryDto;
import org.aegiskb.ai.manage.dto.SystemConfigHistoryRestoreDto;
import org.aegiskb.ai.manage.dto.SystemConfigItemUpdateDto;
import org.aegiskb.ai.manage.vo.SystemConfigCurrentVo;
import org.aegiskb.ai.manage.vo.SystemConfigHistoryItemVo;
import org.aegiskb.ai.manage.vo.SystemConfigHistoryPageVo;

public interface SystemConfigService {

    SystemConfigCurrentVo current();

    SystemConfigCurrentVo updateItem(SystemConfigItemUpdateDto dto, String operatorName);

    SystemConfigHistoryPageVo queryHistory(SystemConfigHistoryPageQueryDto dto);

    SystemConfigHistoryItemVo queryHistoryDetail(SystemConfigHistoryDetailQueryDto dto);

    SystemConfigCurrentVo restore(SystemConfigHistoryRestoreDto dto, String operatorName);
}
