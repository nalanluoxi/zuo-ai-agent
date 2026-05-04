package com.example.zuoaiagent.chatmemory;



import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MapBasedChatMemory implements ChatMemory {

    ConcurrentHashMap<String, List<Message>> chatMemory = new ConcurrentHashMap<>();

    public void add(String conversationId, Message message) {
        if (chatMemory.containsKey(conversationId)){
            chatMemory.get(conversationId).add(message);
        }else {
            List<Message> list=new ArrayList<>();
            list.add(message);
            chatMemory.put(conversationId,list);
        }
        
    }


    public void add(String conversationId, List<Message> messages) {
        if (chatMemory.containsKey(conversationId)){
            messages.forEach(m->chatMemory.get(conversationId).add(m));
        }else {
            chatMemory.put(conversationId,messages);
        }
    }

    public List<Message> get(String conversationId, int lastN) {
        if (chatMemory.containsKey(conversationId)) {
            List<Message> messages = chatMemory.get(conversationId);
            int size = messages.size();
            return messages.subList(Math.max(0, size - lastN), size);
        } else {
            return List.of();
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        if (chatMemory.containsKey(conversationId)){
            return chatMemory.get(conversationId);
        }else {
            return List.of();
        }
    }

    @Override
    public void clear(String conversationId) {
        if (chatMemory.containsKey(conversationId)){
            chatMemory.remove(conversationId);
        }
    }
}
