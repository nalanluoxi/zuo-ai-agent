package com.example.zuoaiagent.controller;


import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.model.Student;
import com.example.zuoaiagent.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {


    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/chat1")
    public BaseResponse<String> chat(@RequestParam String prompt,
                                     @RequestParam(required = false) String conversationId) {
        String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String content = chatService.chat(prompt, conId);
        return ResultUtils.success(content);
    }


    //测试结构化输出
    @PostMapping("/chat2")
    public BaseResponse<Student> chat2(@RequestParam String prompt,
                                     @RequestParam(required = false) String conversationId) {
        String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        Student student = chatService.chat2(prompt, conId);
        return ResultUtils.success(student);
    }

    @PostMapping("/chat/withRag")
    public BaseResponse<String> chatWithRag(@RequestParam String prompt,
                                     @RequestParam(required = false) String conversationId,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String major) {

        String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String aiName =StrUtil.isBlank(name)?"三条":name;
        String majorName =StrUtil.isBlank(major)?"金融、宪法学、投资股价学":major;
        String content = chatService.chatWithRag(prompt, conId,aiName,majorName);
        return ResultUtils.success(content);
    }





}
