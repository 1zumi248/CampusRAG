package com.hznu.campusragbackend.controller;

import com.hznu.campusragbackend.common.Result;
import com.hznu.campusragbackend.model.ChatRequest;
import com.hznu.campusragbackend.model.ChatResponse;
import com.hznu.campusragbackend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public Result<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
            return Result.error(400, "问题不能为空");
        }
        ChatResponse response = chatService.chat(request.getQuestion(), request.getConversationId());
        return Result.ok(response);
    }

    @PostMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<ServerSentEvent<String>>> chatStream(@RequestBody ChatRequest request) {
        Flux<ServerSentEvent<String>> body =
                (request.getQuestion() == null || request.getQuestion().trim().isEmpty())
                        ? Flux.empty()
                        : chatService.streamChat(request.getQuestion(), request.getConversationId());

        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")  // 禁用 nginx/反代缓冲
                .header("Connection", "keep-alive")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }
}
