package org.aegiskb.database.data;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

@Data
public class BaseTableData {

    @TableField(fill = FieldFill.INSERT) // 表示在插入数据时自动填充创建时间
    private Date createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE) // 表示在插入和更新数据时自动填充更新时间
    private Date editTime;

    private Integer status; // 表示数据的状态
}
