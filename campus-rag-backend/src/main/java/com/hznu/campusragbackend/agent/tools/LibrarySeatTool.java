package com.hznu.campusragbackend.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LibrarySeatTool {

    @Tool("查询图书馆座位的实时空闲情况。返回各楼层自习区域的可用座位数量。当用户询问图书馆座位、自习位置时调用此工具")
    public String getLibrarySeats(@P("图书馆楼层或区域，例如二楼、三楼、全部") String area) {
        log.info("图书馆座位查询 area={}", area);
        String floor = area != null && !area.isEmpty() ? area : "全部";
        return String.format("""
                图书馆%s空闲座位（Mock数据）：
                一楼自习区：32个空位（共80个）
                二楼阅览室：18个空位（共60个）
                三楼电子阅览室：25个空位（共50个）
                四楼考研专区：8个空位（共40个）
                五楼期刊区：15个空位（共30个）
                开放时间：7:00-22:00
                """, floor);
    }
}
