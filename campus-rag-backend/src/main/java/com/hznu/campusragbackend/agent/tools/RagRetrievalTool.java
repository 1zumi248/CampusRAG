package com.hznu.campusragbackend.agent.tools;

import com.hznu.campusragbackend.model.SourceReference;
import com.hznu.campusragbackend.rag.retrieval.RetrievalContext;
import com.hznu.campusragbackend.rag.retrieval.RetrievalService;
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

    /** 暂存最近一次检索的 sources，供 ChatService 在回答完成后发送给前端 */
    private final ThreadLocal<List<SourceReference>> lastSources = new ThreadLocal<>();

    @Tool("搜索校园知识库中的文档内容。当用户询问学校规章制度、教务流程、校园生活等问题时，必须先调用此工具获取相关文档，再基于文档回答。返回相关文档片段及编号")
    public String searchKnowledgeBase(@P("搜索查询关键词或问题") String query) {
        log.info("RAG检索工具调用 query={}", query);
        RetrievalContext ctx = retrievalService.retrieveAndFormat(query, 5);
        List<SourceReference> sources = retrievalService.buildSources(ctx.results());
        lastSources.set(sources);

        if (ctx.formattedText().isEmpty()) {
            return "知识库中未找到相关文档。请如实告知用户暂无相关规定。";
        }
        return ctx.formattedText();
    }

    public List<SourceReference> getAndClearSources() {
        List<SourceReference> sources = lastSources.get();
        lastSources.remove();
        return sources;
    }
}
