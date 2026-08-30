package com.example.logmonitor.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.logmonitor.entity.EventDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EventMapper extends BaseMapper<EventDO> {}
