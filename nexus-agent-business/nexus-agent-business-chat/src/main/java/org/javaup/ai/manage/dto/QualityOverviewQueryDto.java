package org.javaup.ai.manage.dto;

import lombok.Data;

@Data
public class QualityOverviewQueryDto {

    /**
     * 统计时间窗天数。空或非法值按默认 30 天处理，"0" 表示全部时间。
     */
    private String windowDays;
}
