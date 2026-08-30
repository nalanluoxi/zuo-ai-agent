package com.example.authservice.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.authservice.entity.UserDO;
import org.apache.ibatis.annotations.Mapper;
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {}
