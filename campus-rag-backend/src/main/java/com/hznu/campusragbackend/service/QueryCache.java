package com.hznu.campusragbackend.service;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.json.JSONUtil;
import com.hznu.campusragbackend.model.ChatResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

import static com.hznu.campusragbackend.common.Constants.CACHE_EXPIRE_SECONDS;
import static com.hznu.campusragbackend.common.Constants.CACHE_KEY_PREFIX;

@Component
@RequiredArgsConstructor
public class QueryCache {

    private final StringRedisTemplate redisTemplate;

    public Optional<ChatResponse> get(String question) {
        String key = buildKey(question);
        String cached = redisTemplate.opsForValue().get(key);
        if (cached == null) return Optional.empty();
        return Optional.of(JSONUtil.toBean(cached, ChatResponse.class));
    }

    public void put(String question, ChatResponse response) {
        String key = buildKey(question);
        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(response),
                Duration.ofSeconds(CACHE_EXPIRE_SECONDS));
    }

    private String buildKey(String question) {
        return CACHE_KEY_PREFIX + DigestUtil.md5Hex(question.trim());
    }
}
