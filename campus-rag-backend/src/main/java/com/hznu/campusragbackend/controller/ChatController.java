package com.hznu.campusragbackend.controller;

import com.hznu.campusragbackend.common.Result;
import com.hznu.campusragbackend.model.ChatRequest;
import com.hznu.campusragbackend.model.ChatResponse;
import com.hznu.campusragbackend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * RAG 问答接口（同步）
     */
    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Result.error(400, "问题不能为空");
        }

        ChatResponse response = chatService.chat(request.getQuestion(), request.getConversationId());
        return Result.ok(response);
    }

    /**
     * RAG 问答接口（流式）
     */
    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request) {
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Flux.just(
                    ServerSentEvent.<String>builder().event("token").data("问题不能为空").build()
            );
        }

        return chatService.streamChat(request.getQuestion(), request.getConversationId());
    }
}
