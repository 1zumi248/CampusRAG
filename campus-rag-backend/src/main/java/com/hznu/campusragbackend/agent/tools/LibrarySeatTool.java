package com.hznu.campusragbackend.agent.tools;

import com.hznu.campusragbackend.config.PromptTemplates;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LibrarySeatTool {

    @Tool(PromptTemplates.TOOL_LIBRARY_SEAT)
    public String getLibrarySeats(@P("图书馆楼层或区域，例如二楼、三楼、全部") String area) {
        log.info("图书馆座位查询 area={}", area);
        String floor = area != null && !area.isEmpty() ? area : "全部";
        return String.format("""
                查询成功|图书馆%s空闲座位：
                一楼自习区：32个空位（共80个）
                二楼阅览室：18个空位（共60个）
                三楼电子阅览室：25个空位（共50个）
                四楼考研专区：8个空位（共40个）
                五楼期刊区：15个空位（共30个）
                开放时间：7:00-22:00
                """, floor);
    }
}
