package com.example.zuoaiagent.controller;


import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.example.zuoaiagent.common.BaseResponse;
import com.example.zuoaiagent.common.ResultUtils;
import com.example.zuoaiagent.model.Student;
import com.example.zuoaiagent.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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
                                            @RequestParam(required = false) String name) {
        String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String aiName = StrUtil.isBlank(name) ? "三条" : name;
        String content = chatService.chatWithRag(prompt, conId, aiName);
        return ResultUtils.success(content);
    }

    @GetMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamChat(@RequestParam String prompt,
                                 @RequestParam(required = false) String conversationId) {
        String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        SseEmitter emitter = new SseEmitter(60_000L);
        chatService.streamChat(prompt, conId, emitter);
        return emitter;
    }

    @GetMapping(value = "/stream/withRag", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamChatWithRag(@RequestParam String prompt,
                                        @RequestParam(required = false) String conversationId,
                                        @RequestParam(required = false) String name) {
        String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String aiName = StrUtil.isBlank(name) ? "三条" : name;
        SseEmitter emitter = new SseEmitter(60_000L);
        chatService.streamChatWithRag(prompt, conId, aiName, emitter);
        return emitter;
    }

    /**
     * 完整 RAG 流水线：问题改写 + 向量检索 + 重排序 + 流式推送
     */
    @GetMapping(value = "/stream/rag/pipeline", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamChatWithRagPipeline(
            @RequestParam String prompt,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "true") boolean enableRewrite,
            @RequestParam(defaultValue = "true") boolean enableRerank) {
        String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String aiName = StrUtil.isBlank(name) ? "三条" : name;
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.streamChatWithRagPipeline(prompt, conId, aiName, enableRewrite, enableRerank, emitter);
        return emitter;
    }

    /**
     * 智能 RAG 流水线：意图检测 + 闲聊短路 + 多通道并行检索 + 重排序 + 流式推送
     * 领域由意图树自动识别填充，无需手动传入
     */
    @GetMapping(value = "/stream/smart", produces = "text/event-stream;charset=UTF-8")
    public SseEmitter streamChatSmart(
            @RequestParam String prompt,
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "true") boolean enableRewrite,
            @RequestParam(defaultValue = "true") boolean enableRerank) {
        String conId = StrUtil.isBlank(conversationId) ? IdUtil.getSnowflakeNextIdStr() : conversationId;
        String aiName = StrUtil.isBlank(name) ? "三条" : name;
        SseEmitter emitter = new SseEmitter(120_000L);
        chatService.streamChatSmart(prompt, conId, aiName, enableRewrite, enableRerank, emitter);
        return emitter;
    }
}
