package com.hznu.campusragbackend.controller;

import com.hznu.campusragbackend.common.Result;
import com.hznu.campusragbackend.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/migrate-to-es")
    public Result<Map<String, Object>> migrateToEs() {
        return Result.ok(adminService.migrateChunksToEs());
    }

    /** 对比纯向量、纯ES、混合检索三组结果 */
    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestParam String q) {
        return Result.ok(adminService.compare(q));
    }
}