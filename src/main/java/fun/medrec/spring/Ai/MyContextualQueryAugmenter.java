package fun.medrec.spring.Ai;



import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.QueryAugmenter;
import org.springframework.ai.rag.util.PromptAssert;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * @author 彭超
 * @version 1.0
 * @description 重新源码以解决历史消息丢失问题， 重新augment方法
 * @date 2026-04-24 20:24
 */
public final class MyContextualQueryAugmenter implements QueryAugmenter {

    private static final Logger logger = LoggerFactory.getLogger(MyContextualQueryAugmenter.class);
    private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
			Context information is below.

			---------------------
			{context}
			---------------------

			Given the context information and no prior knowledge, answer the query.

			Follow these rules:

			1. If the answer is not in the context, just say that you don't know.
			2. Avoid statements like "Based on the context..." or "The provided information...".

			Query: {query}

			Answer:
			""");

    private static final PromptTemplate DEFAULT_EMPTY_CONTEXT_PROMPT_TEMPLATE = new PromptTemplate("""
			The user query is outside your knowledge base.
			Politely inform the user that you can't answer it.
			""");

    private static final boolean DEFAULT_ALLOW_EMPTY_CONTEXT = false;

    /**
     * Default document formatter that just joins document text with newlines
     */
    private static final Function<List<Document>, String> DEFAULT_DOCUMENT_FORMATTER = documents -> documents.stream()
            .map(Document::getText)
            .collect(Collectors.joining(System.lineSeparator()));

    private final PromptTemplate promptTemplate;

    private final PromptTemplate emptyContextPromptTemplate;

    private final boolean allowEmptyContext;

    private final Function<List<Document>, String> documentFormatter;

    public MyContextualQueryAugmenter(@Nullable PromptTemplate promptTemplate,
                                    @Nullable PromptTemplate emptyContextPromptTemplate, @Nullable Boolean allowEmptyContext,
                                    @Nullable Function<List<Document>, String> documentFormatter) {
        this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
        this.emptyContextPromptTemplate = emptyContextPromptTemplate != null ? emptyContextPromptTemplate
                : DEFAULT_EMPTY_CONTEXT_PROMPT_TEMPLATE;
        this.allowEmptyContext = allowEmptyContext != null ? allowEmptyContext : DEFAULT_ALLOW_EMPTY_CONTEXT;
        this.documentFormatter = documentFormatter != null ? documentFormatter : DEFAULT_DOCUMENT_FORMATTER;
        PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "query", "context");
    }

    /**
     * @description 源代码会创建一个新的查询，所以会丢失历史消息，重新的代码基于旧查询创建，保留历史消息
     */
    @Override
    public @NotNull Query augment(@NotNull Query query, @NotNull List<Document> documents) {
        Assert.notNull(query, "query cannot be null");
        Assert.notNull(documents, "documents cannot be null");
        logger.debug("Augmenting query with contextual data");

        if (documents.isEmpty()) {
            return augmentQueryWhenEmptyContext(query);
        }

        // 1. Collect content from documents.
        String documentContext = this.documentFormatter.apply(documents);

        // 2. Define prompt parameters.
        Map<String, Object> promptParameters = Map.of("query", query.text(), "context", documentContext);

        // 3. Augment user prompt with document context.
        // 源码是新建一个请求从而丢失历史消息：return new Query(this.promptTemplate.render(promptParameters));
        String augmentedText = this.promptTemplate.render(promptParameters);
        return query.mutate().text(augmentedText).build();
    }

    private Query augmentQueryWhenEmptyContext(Query query) {
        if (this.allowEmptyContext) {
            logger.debug("Empty context is allowed. Returning the original query.");
            return query;
        }
        logger.debug("Empty context is not allowed. Returning a specific query for empty context.");
        return new Query(this.emptyContextPromptTemplate.render());
    }

    public static MyContextualQueryAugmenter.Builder builder() {
        return new MyContextualQueryAugmenter.Builder();
    }

    public static final class Builder {

        private PromptTemplate promptTemplate;

        private PromptTemplate emptyContextPromptTemplate;

        private Boolean allowEmptyContext;

        private Function<List<Document>, String> documentFormatter;

        public MyContextualQueryAugmenter.Builder promptTemplate(PromptTemplate promptTemplate) {
            this.promptTemplate = promptTemplate;
            return this;
        }

        public MyContextualQueryAugmenter.Builder emptyContextPromptTemplate(PromptTemplate emptyContextPromptTemplate) {
            this.emptyContextPromptTemplate = emptyContextPromptTemplate;
            return this;
        }

        public MyContextualQueryAugmenter.Builder allowEmptyContext(Boolean allowEmptyContext) {
            this.allowEmptyContext = allowEmptyContext;
            return this;
        }

        public MyContextualQueryAugmenter.Builder documentFormatter(Function<List<Document>, String> documentFormatter) {
            this.documentFormatter = documentFormatter;
            return this;
        }

        public MyContextualQueryAugmenter build() {
            return new MyContextualQueryAugmenter(this.promptTemplate, this.emptyContextPromptTemplate,
                    this.allowEmptyContext, this.documentFormatter);
        }

    }

}

