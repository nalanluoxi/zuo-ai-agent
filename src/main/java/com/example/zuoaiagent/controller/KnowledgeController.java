package com.example.zuoaiagent.controller;


import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

//@RestController
//@RequestMapping("/knowledge")
public class KnowledgeController {

    /**
     * TODO 创建知识库
     * 修改知识库
     * 上传文档
     * 文档分片
     */

    //@PostMapping("/createKnowledge")
    public BaseResponse<String> createKnowledge() {
       // String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        //String content = chatService.chat(prompt, conId);
        //return ResultUtils.success(content);
        return null;
    }



}
