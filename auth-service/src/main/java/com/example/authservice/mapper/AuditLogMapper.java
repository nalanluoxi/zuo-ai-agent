package com.example.authservice.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authservice.entity.AuditLogDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogDO> {}
