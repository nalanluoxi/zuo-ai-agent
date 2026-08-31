package com.example.zuoaiagent.service.impl;

import com.example.zuoaiagent.advisor.MyLoggerAdvisor;
import com.example.zuoaiagent.chat.RoutingChatService;
import com.example.zuoaiagent.intent.model.IntentResult;
import com.example.zuoaiagent.intent.service.IntentClassifier;
import com.example.zuoaiagent.model.Student;
import com.example.zuoaiagent.pipeline.RagPipelineContext;
import com.example.zuoaiagent.pipeline.SmartRagPipeline;
import com.example.zuoaiagent.prompt.PromptTemplateLoader;
import com.example.zuoaiagent.rag.DocumentReranker;
import com.example.zuoaiagent.rag.QueryRewriter;
import com.example.zuoaiagent.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ChatServiceImpl implements ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatServiceImpl.class);
    private static final String SYSTEM_MASTER_TEMPLATE = "prompts/system-master.st";

    @Autowired
    private ChatClient chatClient;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private RoutingChatService routingChatService;

    @Autowired
    private QueryRewriter queryRewriter;

    @Autowired
    private DocumentReranker documentReranker;

    @Autowired
    private SmartRagPipeline smartRagPipeline;

    @Autowired
    private IntentClassifier intentClassifier;

    @Autowired
    private PromptTemplateLoader templateLoader;

    @Override
    public String chat(String prompt, String conId) {
        ChatResponse response = chatClient
                .prompt()
                .user(prompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(conId).build())
                .call()
                .chatResponse();
        String content = response.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Override
    public Student chat2(String prompt, String conId) {
        Student student = chatClient
                .prompt()
                .user(prompt)
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory).conversationId(conId).build())
                .call()
                .entity(Student.class);
        return student;
    }

    @Override
    public String chatWithRag(String prompt, String conId, String name) {
        String systemPrompt = templateLoader.render(SYSTEM_MASTER_TEMPLATE, Map.of("name", name, "domain", "各领域"));
        log.info("系统提示词:{}", systemPrompt);

        ChatResponse chatResponse = chatClient
                .prompt()
                .system(systemPrompt)
                .user(prompt)
                .advisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).conversationId(conId).build(),
                        new MyLoggerAdvisor(),
                        new QuestionAnswerAdvisor(vectorStore)
                )
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Override
    public void streamChat(String prompt, String conversationId, SseEmitter emitter) {
        routingChatService.streamChat(prompt, conversationId, null, null, emitter);
    }

    @Override
    public void streamChatWithRag(String prompt, String conversationId, String name, SseEmitter emitter) {
        String systemPrompt = templateLoader.render(SYSTEM_MASTER_TEMPLATE, Map.of("name", name, "domain", "各领域"));
        routingChatService.streamChat(prompt, conversationId, systemPrompt, vectorStore, emitter);
    }


    @Override
    public void streamChatWithRagPipeline(String prompt, String conversationId, String name,
                                          boolean enableRewrite, boolean enableRerank, SseEmitter emitter) {
        String searchQuery = enableRewrite ? queryRewriter.rewrite(prompt) : prompt;
        log.info("[RagPipeline] conId={} 改写: {} → {}", conversationId, prompt, searchQuery);

        IntentResult intentResult = intentClassifier.classify(searchQuery);
        String domain = resolveDomain(intentResult);
        log.info("[RagPipeline] conId={} 意图: {}，领域: {}", conversationId, intentResult, domain);

        List<Document> retrieved = vectorStore.similaritySearch(
                SearchRequest.builder().query(searchQuery).topK(6).build());
        log.info("[RagPipeline] conId={} 检索到 {} 个文档片段", conversationId, retrieved.size());

        List<Document> reranked = enableRerank
                ? documentReranker.rerank(searchQuery, retrieved, 3)
                : (retrieved.size() > 3 ? retrieved.subList(0, 3) : retrieved);
        log.info("[RagPipeline] conId={} 重排序后保留 {} 个文档片段", conversationId, reranked.size());

        String context = reranked.stream()
                .map(Document::getFormattedContent)
                .collect(Collectors.joining("\n\n---\n\n"));

        String systemPrompt = templateLoader.render(SYSTEM_MASTER_TEMPLATE, Map.of("name", name, "domain", domain));
        String finalSystemPrompt = systemPrompt + "\n\n【参考知识库内容】\n" + context;

        routingChatService.streamChat(prompt, conversationId, finalSystemPrompt, null, emitter);
    }

    @Override
    public void streamChatSmart(String prompt, String conversationId, String name,
                                boolean enableRewrite, boolean enableRerank,
                                boolean enableMemory, Long userId, boolean ragEnabled,
                                SseEmitter emitter) {
        if (!ragEnabled) {
            log.info("[ChatService] RAG 已关闭，纯模型对话, conversationId={}", conversationId);
            routingChatService.streamChat(prompt, conversationId, null, null, emitter, false);
            return;
        }
        RagPipelineContext ctx = new RagPipelineContext(
                prompt, conversationId, name, enableRewrite, enableRerank, enableMemory, userId);
        smartRagPipeline.execute(ctx, emitter);
    }

    @Override
    public String ask(String prompt, String conversationId) {
        return routingChatService.chat(prompt, conversationId, null, null);
    }

    private String resolveDomain(IntentResult intentResult) {
        if (intentResult == null) return "各领域";
        if (intentResult.isSystem() || intentResult.getConfidence() == 0.0) return "各领域";
        return intentResult.getLabel();
    }
}
