package fun.medrec.spring.domain.Ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.medrec.spring.domain.entity.Agent;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.*;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.memory.repository.jdbc.MysqlChatMemoryRepositoryDialect;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS;

@Slf4j
public class AiAgent {
    public static class DocumentAdvisor implements BaseAdvisor {
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

    private static String modelName;
    private static OpenAiApi openAiApi;
    private static JdbcTemplate jdbcTemplate;
    private static final String TEMPLATE = """
			用户问题：{query}

			相关上下文：
			---------------------
			{question_answer_context}
			---------------------

			回答规则：
			1. 严格基于上述上下文信息回答
			2. 不要使用你的先验知识
			3. 如果上下文中没有相关信息，请回复："抱歉，根据当前知识库，我无法回答这个问题"
			4. 回答要简洁、准确

			请回答：
			""";

    private ChatClient client;
    private Agent agent;
    private List<MyVectorStore> stores = new ArrayList<>();
    private SearchRequest searchRequest;
    private List<Document> documents = new ArrayList<>();

    @Getter
    private static Cache<Integer, AiAgent> agentCache;

    public static void init(JdbcTemplate jdbcTemplate, String baseUrl, String apiKey, String modelName) {
        AiAgent.modelName = modelName;
        AiAgent.jdbcTemplate = jdbcTemplate;
        AiAgent.openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        AiAgent.agentCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    public static AiAgent getAgent(Integer id) {
        return agentCache.getIfPresent(id);
    }

    public static void deleteAgent(Integer id) {
        agentCache.invalidate(id);
    }

    public AiAgent(Agent agent) {
        this.agent = agent;
        OpenAiChatOptions openAiChatOptions = OpenAiChatOptions.builder()
                .model(modelName)
                .temperature(agent.getTemperature())
                .maxTokens(10000)
                .build();
        OpenAiChatModel model = OpenAiChatModel.builder()
                .openAiApi(AiAgent.openAiApi)
                .defaultOptions(openAiChatOptions)
                .build();
        ChatMemoryRepository chatMemoryRepository = JdbcChatMemoryRepository.builder()
                .jdbcTemplate(jdbcTemplate)
                .dialect(new MysqlChatMemoryRepositoryDialect())
                .build();
        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(agent.getMaxMessage())
                .build();

        DocumentAdvisor documentAdvisor = new DocumentAdvisor(this.documents);

        client = ChatClient
                .builder(model)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        documentAdvisor
                )
                .build();

        searchRequest = SearchRequest.builder().similarityThreshold(agent.getSimilarity()).topK(agent.getTopK()).build();
        AiAgent.agentCache.put(agent.getId(), this);
    }

    public Flux<String> chat(String sentence) {
        ChatClient.ChatClientRequestSpec chatClientRequestSpec = client
                .prompt()
                .system(agent.getPrompt())
                .user(sentence);

        for (MyVectorStore store: stores) {
            PromptTemplate customPromptTemplate = PromptTemplate.builder()
                    .renderer(StTemplateRenderer.builder().startDelimiterToken('{').endDelimiterToken('}').build())
                    .template(TEMPLATE)
                    .build();
            QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(store.getRedisVectorStore())
                    .promptTemplate(customPromptTemplate)
                    .searchRequest(searchRequest)
                    .build();
            chatClientRequestSpec.advisors(advisor);
        }

        return chatClientRequestSpec.advisors(
                a -> a.param(ChatMemory.CONVERSATION_ID, agent.getId())
        )
                .stream()
                .content();


    }

    public void delete(int id) {
        String sql = "delete from SPRING_AI_CHAT_MEMORY where id = ? and conversation_id = ?";
        jdbcTemplate.update(sql, id, agent.getId());
    }

    public void deleteAll() {
        String sql = "delete from SPRING_AI_CHAT_MEMORY where conversation_id = ?";
        jdbcTemplate.update(sql, agent.getId() + "");

    }

    public void addVectorStore(MyVectorStore vectorStore) {
        stores.add(vectorStore);
    }

    public void deleteVectorStore(int id) {
        stores.removeIf(store -> store.getVector().getId() == id);
    }
}
