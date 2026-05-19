package com.hznu.campusragbackend.controller;

import com.hznu.campusragbackend.common.Result;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    /**
     * 测试聊天模型是否可用
     */
    @GetMapping("/chat")
    public Result<Map<String, Object>> testChat(@RequestParam(defaultValue = "你好，请介绍一下自己") String question) {
        try {
            log.info("开始测试聊天模型，问题: {}", question);
            
            long startTime = System.currentTimeMillis();
            String answer = chatModel.chat(question);
            long endTime = System.currentTimeMillis();
            
            Map<String, Object> result = new HashMap<>();
            result.put("question", question);
            result.put("answer", answer);
            result.put("responseTimeMs", endTime - startTime);
            result.put("status", "success");
            
            log.info("聊天模型测试成功，耗时: {}ms", endTime - startTime);
            return Result.ok(result);
            
        } catch (Exception e) {
            log.error("聊天模型测试失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", e.getMessage());
            return Result.error(500, "聊天模型测试失败: " + e.getMessage());
        }
    }

    /**
     * 测试向量模型是否可用
     */
    @GetMapping("/embedding")
    public Result<Map<String, Object>> testEmbedding(@RequestParam(defaultValue = "这是一个测试文本") String text) {
        try {
            log.info("开始测试向量模型，文本长度: {}", text.length());
            
            long startTime = System.currentTimeMillis();
            Embedding embedding = embeddingModel.embed(text).content();
            long endTime = System.currentTimeMillis();
            
            float[] vector = embedding.vector();
            
            Map<String, Object> result = new HashMap<>();
            result.put("text", text);
            result.put("vectorDimension", vector.length);
            result.put("vectorSample", getVectorSample(vector)); // 只返回前10个值作为示例
            result.put("responseTimeMs", endTime - startTime);
            result.put("status", "success");
            
            log.info("向量模型测试成功，维度: {}, 耗时: {}ms", vector.length, endTime - startTime);
            return Result.ok(result);
            
        } catch (Exception e) {
            log.error("向量模型测试失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("status", "error");
            errorResult.put("message", e.getMessage());
            return Result.error(500, "向量模型测试失败: " + e.getMessage());
        }
    }

    /**
     * 综合测试：同时测试聊天和向量模型
     */
    @GetMapping("/all")
    public Result<Map<String, Object>> testAll() {
        try {
            log.info("开始综合测试所有AI模型");
            
            Map<String, Object> result = new HashMap<>();
            
            // 测试聊天模型
            long chatStart = System.currentTimeMillis();
            String chatAnswer = chatModel.chat("请用一句话介绍RAG技术");
            long chatEnd = System.currentTimeMillis();
            
            result.put("chatTest", Map.of(
                    "status", "success",
                    "answer", chatAnswer,
                    "responseTimeMs", chatEnd - chatStart
            ));
            
            // 测试向量模型
            long embedStart = System.currentTimeMillis();
            Embedding embedding = embeddingModel.embed("RAG技术").content();
            long embedEnd = System.currentTimeMillis();
            
            result.put("embeddingTest", Map.of(
                    "status", "success",
                    "dimension", embedding.vector().length,
                    "responseTimeMs", embedEnd - embedStart
            ));
            
            result.put("overallStatus", "success");
            log.info("综合测试完成");
            return Result.ok(result);
            
        } catch (Exception e) {
            log.error("综合测试失败", e);
            return Result.error(500, "综合测试失败: " + e.getMessage());
        }
    }

    /**
     * 获取向量的前N个值作为示例
     */
    private float[] getVectorSample(float[] vector) {
        int sampleSize = Math.min(10, vector.length);
        float[] sample = new float[sampleSize];
        System.arraycopy(vector, 0, sample, 0, sampleSize);
        return sample;
    }
}
