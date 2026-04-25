package fun.medrec.spring.Ai;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT;

/**
 * @author 彭超
 * @version 1.0
 * @description 存储检索到的文档，并存储到aiAgent
 * @date 2026-04-24 20:29
 */
@Slf4j
public class DocumentAdvisor implements BaseAdvisor {
    private final AiAgent aiAgent;

    public DocumentAdvisor(AiAgent aiAgent) {
        this.aiAgent = aiAgent;
    }


    @NotNull
    @Override
    public String getName() {
        return "DocumentAdvisor";
    }

    @NotNull
    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, @NotNull AdvisorChain advisorChain) {
        Map<String, Object> context = chatClientRequest.context();
        Object o = context.get(DOCUMENT_CONTEXT);
        List<Document> documents;
        if (o instanceof List<?>) {
            documents = ((List<?>) o).stream()
                    .filter(Document.class::isInstance)
                    .map(Document.class::cast)
                    .toList();
        } else {
            documents = Collections.emptyList();
        }        this.aiAgent.setDocuments(documents);

        return chatClientRequest;
    }

    @NotNull
    @Override
    public ChatClientResponse after(@NotNull ChatClientResponse chatClientResponse, @NotNull AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 999;
    }
}

