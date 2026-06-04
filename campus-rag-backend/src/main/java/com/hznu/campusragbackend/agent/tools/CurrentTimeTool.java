package com.hznu.campusragbackend.agent.tools;

import com.hznu.campusragbackend.config.PromptTemplates;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class CurrentTimeTool {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm:ss (EEEE)");

    @Tool(PromptTemplates.TOOL_CURRENT_TIME)
    public String getCurrentTime() {
        String now = LocalDateTime.now().format(FORMATTER);
        log.info("当前时间查询: {}", now);
        return "当前时间：" + now;
    }
}
