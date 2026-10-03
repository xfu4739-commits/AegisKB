package org.aegiskb.ai.manage.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.aegiskb.ai.manage.data.SuperAgentDocument;

import java.util.List;

@Mapper
public interface SuperAgentDocumentMapper extends BaseMapper<SuperAgentDocument> {

    @Select("SELECT * FROM aegis_kb_document WHERE status = 1 ORDER BY id FOR UPDATE")
    List<SuperAgentDocument> selectActiveForGraphProjection();
}
