package org.aegiskb.ai.manage.service;

import org.aegiskb.ai.manage.dto.QualityOverviewQueryDto;
import org.aegiskb.ai.manage.vo.QualityOverviewVo;

public interface QualityOverviewService {

    /**
     * 查询知识运行全景：路由走势、通道剖面、裁决结构与文档处理耗时。
     */
    QualityOverviewVo queryQualityOverview(QualityOverviewQueryDto dto);
}
