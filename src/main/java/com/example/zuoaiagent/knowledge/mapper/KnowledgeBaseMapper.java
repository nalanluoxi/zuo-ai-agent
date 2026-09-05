package com.example.zuoaiagent.knowledge.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.example.zuoaiagent.knowledge.entity.KnowledgeBaseDO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeBaseMapper extends BaseMapper<KnowledgeBaseDO> {

    /**
     * 分页查询（绕过租户拦截器，由 Service 层手动控制可见性：自己的 + 全局 PUBLIC + 本租户 TEAM）
     */
    @InterceptorIgnore(tenantLine = "1")
    @Select("SELECT * FROM t_knowledge_base ${ew.customSqlSegment}")
    IPage<KnowledgeBaseDO> selectPageIgnoreTenant(IPage<KnowledgeBaseDO> page,
                                                  @Param(Constants.WRAPPER) Wrapper<KnowledgeBaseDO> queryWrapper);

    /**
     * 按 ID 查询（绕过租户拦截器，由 Service 层做访问校验）
     */
    @InterceptorIgnore(tenantLine = "1")
    @Select("SELECT * FROM t_knowledge_base WHERE id = #{id} AND deleted = 0")
    KnowledgeBaseDO selectByIdIgnoreTenant(Long id);
}
