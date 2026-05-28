package com.hznu.campusragbackend.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ClassroomTool {

    @Tool("查询空闲教室信息，返回当前时间段可用的教室列表。当用户询问空闲教室、自习教室、教室占用情况时调用此工具")
    public String getEmptyClassrooms(@P("教学楼名称或区域，例如教三楼、信息楼、全部") String building) {
        log.info("空闲教室查询 building={}", building);
        String bld = building != null && !building.isEmpty() ? building : "全部教学楼";
        return String.format("""
                %s 当前空闲教室（Mock数据）：
                教一楼：101(60座)、203(40座)、305(30座)
                教二楼：201(80座)、402(50座)
                教三楼：102(60座)、301(40座)、502(30座)
                信息楼：201(50座)、304(40座)、405(30座)
                综合楼：101(100座)、202(60座)
                查询时间：当前时段（第3-4节）
                """, bld);
    }
}
