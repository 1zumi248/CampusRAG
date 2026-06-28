package com.hznu.campusragbackend.admin;

import com.hznu.campusragbackend.common.Result;
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
}
