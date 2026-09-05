package com.example.zuoaiagent.knowledge.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.example.zuoaiagent.knowledge.entity.KnowledgeDocumentDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocumentDO> {

    /**
     * 分页查询（绕过租户拦截器，调用前必须先通过 KnowledgeBaseService.checkKbAccess 校验）
     */
    @InterceptorIgnore(tenantLine = "1")
    @Select("SELECT * FROM t_knowledge_document ${ew.customSqlSegment}")
    IPage<KnowledgeDocumentDO> selectPageIgnoreTenant(IPage<KnowledgeDocumentDO> page,
                                                      @Param(Constants.WRAPPER) Wrapper<KnowledgeDocumentDO> queryWrapper);
}
