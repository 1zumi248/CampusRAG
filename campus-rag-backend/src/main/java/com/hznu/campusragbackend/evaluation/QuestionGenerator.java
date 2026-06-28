package com.hznu.campusragbackend.evaluation;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 用 JDK HttpClient 直接调 DashScope，绕过 LC4j 的同步 RestClient 超时限制 */
@Slf4j
@Service
@Profile("!prod")
public class QuestionGenerator {

    private final HttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final String modelName;

    public QuestionGenerator(
            @Value("${langchain4j.open-ai.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.modelName = modelName;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String generate(String title, String content) {
        String clip = content.length() > 800 ? content.substring(0, 800) : content;
        String userMsg = String.format("文档标题：%s\n文档片段：\n%s\n\n问题：", title, clip);

        JSONObject systemMsg = new JSONObject();
        systemMsg.set("role", "system");
        systemMsg.set("content", "你是评测数据生成器。根据文档片段生成一个具体的问题。只返回问题本身。");

        JSONObject userMessage = new JSONObject();
        userMessage.set("role", "user");
        userMessage.set("content", userMsg);

        JSONArray messages = new JSONArray();
        messages.add(systemMsg);
        messages.add(userMessage);

        JSONObject body = new JSONObject();
        body.set("model", modelName);
        body.set("messages", messages);
        body.set("temperature", 0.7);
        body.set("max_tokens", 100);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("DashScope 返回非200: {} body={}", response.statusCode(), response.body().substring(0, Math.min(200, response.body().length())));
                return "";
            }

            JSONObject respJson = JSONUtil.parseObj(response.body());
            JSONArray choices = respJson.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) return "";
            return choices.getJSONObject(0).getJSONObject("message").getStr("content");

        } catch (Exception e) {
            log.warn("DashScope 调用失败: {}", e.getMessage());
            return "";
        }
    }
}
