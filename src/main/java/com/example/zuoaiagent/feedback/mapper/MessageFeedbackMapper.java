package com.example.zuoaiagent.feedback.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.zuoaiagent.feedback.entity.MessageFeedbackDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 消息反馈 Mapper
 */
@Mapper
public interface MessageFeedbackMapper extends BaseMapper<MessageFeedbackDO> {
}
