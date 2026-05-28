package com.hznu.campusragbackend.agent.tools;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ScheduleTool {

    @Tool("查询指定班级或专业的课程表。返回课程名称、上课时间、地点、任课教师等信息。当用户询问课表、课程安排时调用此工具")
    public String getSchedule(@P("班级或专业名称，例如软件工程2101、计算机科学2202") String className) {
        log.info("课表查询 className={}", className);
        return String.format("""
                %s 本周课程表（Mock数据）：
                周一：1-2节 高等数学（教三楼201，张教授）
                周一：3-4节 大学英语（外语楼305，李老师）
                周二：1-2节 数据结构（信息楼102，王教授）
                周二：5-6节 思想政治（综合楼301，赵老师）
                周三：3-4节 操作系统（信息楼204，陈教授）
                周四：1-2节 数据库原理（信息楼301，刘教授）
                周五：3-4节 体育（体育馆）
                周五：7-8节 形势与政策（综合楼201，马老师）
                """, className);
    }
}
