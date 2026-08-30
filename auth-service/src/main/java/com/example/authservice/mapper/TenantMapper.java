package com.example.authservice.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authservice.entity.TenantDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface TenantMapper extends BaseMapper<TenantDO> {}
