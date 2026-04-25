package fun.medrec.spring.Ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Builder
public class MyQueryExpander  implements QueryExpander {
    @Builder.Default
    private Integer n = 4;

    @NotNull
    private ChatClient chatClient;

    private static String QUERY_TEMPLATE = """
        你是RAG检索优化专家。
        
        任务：
        1. 根据用户当前问题 + 对话历史，理解用户真正想查询的完整意图。
        2. 生成 {number} 个**完整、通顺、适合向量检索**的查询句子。
        
        输入：
        - 用户问题：{query}
        - 对话历史：{history}
        - 生成数量：{number}
        
        严格遵守以下规则：
        1. 必须是**完整通顺的查询句子**，不要只输出关键词。
        2. 句子简短明确，符合用户真实提问方式，适合向量检索。
        3. 不描述过程，不冗余，不解释。
        4. 友好问题（你好、你是谁）直接返回原问题。
        5. 输出：每行一个变体，无序号、无多余内容。
        
        现在输出变体：
        """;

    @Builder.Default
    private PromptTemplate promptTemplate = new PromptTemplate(QUERY_TEMPLATE);

    @Override
    public @NotNull List<Query> expand(@NotNull Query query) {
        Assert.notNull(query, "query cannot be null");
        Map<String, Object> vars = new HashMap<>();
        vars.put("query", query.text());
        vars.put("history", formatConversationHistory(query.history()));
        vars.put("number", n);
        String user = this.promptTemplate.render(vars);
        log.debug("重构查询语句");

        String content = this.chatClient.prompt()
                .user(user)
                .call()
                .content();

        if (!StringUtils.hasText(content)) {
            log.warn("Query compression result is null/empty. Returning the input query unchanged.");
            return List.of(query);
        }

        List<String> contents = Arrays.asList(content.split("\n"));

        if (CollectionUtils.isEmpty(contents) || this.n != contents.size()) {
            log.warn(
                    "Query expansion result does not contain the requested {} variants. Returning the input query unchanged.",
                    this.n);
            return List.of(query);
        }

        List<Query> collect = contents.stream()
                .filter(StringUtils::hasText)
                .map(queryText -> query.mutate().text(queryText).build())
                .collect(Collectors.toCollection(ArrayList::new));

        collect.addFirst(query);
        log.debug("扩展后的查询语句：{}", JSON.toJSONString( collect.stream().map(Query::text).toList(), SerializerFeature.PrettyFormat));

        return collect;
    }

    private String formatConversationHistory(List<Message> history) {
        if (history.isEmpty()) {
            return "";
        }

        return history.stream()
                .filter(message -> message.getMessageType().equals(MessageType.USER)
                        || message.getMessageType().equals(MessageType.ASSISTANT))
                .map(message -> "%s: %s".formatted(message.getMessageType(), message.getText()))
                .collect(Collectors.joining("\n"));
    }
}
