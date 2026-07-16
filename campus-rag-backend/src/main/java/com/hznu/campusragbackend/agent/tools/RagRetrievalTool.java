package com.hznu.campusragbackend.agent.tools;

import com.hznu.campusragbackend.model.SourceReference;
import com.hznu.campusragbackend.rag.retrieval.RetrievalContext;
import com.hznu.campusragbackend.rag.retrieval.RetrievalResult;
import com.hznu.campusragbackend.rag.retrieval.RetrievalService;
import com.hznu.campusragbackend.config.PromptTemplates;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RagRetrievalTool {

    private final RetrievalService retrievalService;

    /** 暂存最近一次检索的原始结果(含三路分数和排名),供 ChatService 构建调试面板 */
    private final ThreadLocal<List<RetrievalResult>> lastResults = new ThreadLocal<>();

    @Tool(PromptTemplates.TOOL_RAG_RETRIEVAL)
    public String searchKnowledgeBase(@P("搜索查询关键词或问题") String query) {
        log.info("RAG检索工具调用 query={}", query);
        RetrievalContext ctx = retrievalService.retrieveAndFormat(query, 5);
        lastResults.set(ctx.results());

        if (ctx.formattedText().isEmpty()) {
            return "知识库中未找到相关文档。请如实告知用户暂无相关规定。";
        }
        return ctx.formattedText();
    }

    /** 取出并清除暂存的检索结果 */
    public List<RetrievalResult> getAndClearResults() {
        List<RetrievalResult> results = lastResults.get();
        lastResults.remove();
        return results;
    }

    /** 基于暂存结果构建 sources(带或不带调试字段) */
    public List<SourceReference> buildSources(List<RetrievalResult> results, boolean withDebug) {
        if (results == null || results.isEmpty()) return List.of();
        return retrievalService.buildSources(results, withDebug);
    }

    /** 兼容旧调用:返回不带调试字段的 sources */
    public List<SourceReference> getAndClearSources() {
        return buildSources(getAndClearResults(), false);
    }
}
