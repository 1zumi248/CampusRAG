package com.hznu.campusragbackend.rag.assistant;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

@AiService
public interface RagAssistant {

    /**
     * 关键设计：
     * - 检索上下文放在 @SystemMessage 中（动态注入），LangChain4j 不会将其存入 ChatMemory
     * - 用户问题放在 @UserMessage 中（无冗余上下文），只会将干净的问答对持久化到 chat_memory
     */
    @SystemMessage("""
            你是校园知识问答助手，专门回答关于学校规章制度、校园生活、教务流程等问题。

            请严格遵循以下规则：
            1. 只能基于提供的文档内容回答问题
            2. 必须在答案中标注引用来源，格式如[1][2]
            3. 如果文档中没有相关信息，回答'暂无相关规定'
            4. 回答应简洁明了，突出重点

            以下是本次检索到的相关文档内容：
            {{context}}
            """)
    @UserMessage("{{question}}")
    String answer(@V("context") String context, @V("question") String question, @MemoryId Long conversationId);

    @SystemMessage("""
            你是校园知识问答助手，专门回答关于学校规章制度、校园生活、教务流程等问题。
            基于文档内容完成用户要求，如果文档中不存在就联网查找或生成
            
            以下是本次检索到的相关文档内容：
            {{context}}
            """)
    @UserMessage("{{question}}")
    TokenStream stream(@V("context") String context, @V("question") String question, @MemoryId Long conversationId);
}
