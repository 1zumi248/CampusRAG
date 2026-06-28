package com.hznu.campusragbackend.evaluation;

import com.hznu.campusragbackend.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 评测专用，仅开发环境加载，生产部署不注册 */
@RestController
@RequestMapping("/admin/eval")
@Profile("!prod")
@RequiredArgsConstructor
public class EvalController {

    private final EvaluationService evaluationService;

    @PostMapping("/generate")
    public Result<Map<String, Object>> generateEvalSet() {
        return Result.ok(evaluationService.generateTestSet());
    }

    @GetMapping("/run")
    public Result<Map<String, Object>> runEval(@RequestParam(defaultValue = "5") int topK) {
        return Result.ok(evaluationService.evaluate(topK));
    }

    @GetMapping("/testset")
    public Result<?> getTestSet() {
        return Result.ok(evaluationService.getTestSet());
    }

    /** 对比纯向量、纯ES、混合检索三组结果 */
    @GetMapping("/compare")
    public Result<Map<String, Object>> compare(@RequestParam String q) {
        return Result.ok(evaluationService.compare(q));
    }
}
