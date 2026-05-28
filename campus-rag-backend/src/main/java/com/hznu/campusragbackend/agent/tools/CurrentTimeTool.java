package com.hznu.campusragbackend.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class CurrentTimeTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss (EEEE)");

    @Tool("获取当前准确的日期和时间。当用户询问今天日期、当前时间、现在几点、今天几号时必须调用此工具，不要依赖模型自身的日期认知")
    public String getCurrentTime() {
        String now = LocalDateTime.now().format(FORMATTER);
        log.info("当前时间查询: {}", now);
        return "当前时间：" + now;
    }
}
