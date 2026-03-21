package fun.medrec.spring.Ai;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS;

public class DocumentAdvisor implements BaseAdvisor {
    private List<Document> documents;

    public DocumentAdvisor(List<Document> documents) {
        this.documents = documents;
    }


    @Override
    public String getName() {
        return "DocumentAdvisor";
    }

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        Map<String, Object> context = chatClientRequest.context();
        this.documents  = (List<Document>)context.get(RETRIEVED_DOCUMENTS);

        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        return chatClientResponse;
    }

    @Override
    public int getOrder() {
        return 999;
    }
}

