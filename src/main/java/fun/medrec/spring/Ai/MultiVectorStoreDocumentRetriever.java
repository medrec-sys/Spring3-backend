package fun.medrec.spring.Ai;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.List;
import java.util.function.Supplier;

public final class MultiVectorStoreDocumentRetriever implements DocumentRetriever {
    private final List<VectorStoreDocumentRetriever> retrievers;

    public MultiVectorStoreDocumentRetriever(List<VectorStore> vectorStores, @Nullable Double similarityThreshold,
                                             Integer topK, @Nullable Supplier<Filter.Expression> filterExpression) {
        this.retrievers = vectorStores.stream().map(vectorStore -> VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(similarityThreshold != null ? similarityThreshold
                        : SearchRequest.SIMILARITY_THRESHOLD_ACCEPT_ALL)
                .topK(topK)
                .filterExpression(filterExpression != null ? filterExpression : () -> null)
                .build()).toList();
    }

    @NotNull
    @Override
    public List<Document> retrieve(@NotNull Query query) {
        Assert.notNull(query, "query cannot be null");
        return retrievers.stream().map(retriever -> retriever.retrieve(query)).flatMap(List::stream)
                .toList();
    }

    public static MultiVectorStoreDocumentRetriever.Builder builder() {
        return new MultiVectorStoreDocumentRetriever.Builder();
    }

    public static final class Builder {

        private List<VectorStore> vectorStores;

        private Double similarityThreshold;

        private Integer topK;

        private Supplier<Filter.Expression> filterExpression;

        private Builder() {
        }

        public MultiVectorStoreDocumentRetriever.Builder vectorStores(List<VectorStore> vectorStores) {
            this.vectorStores = vectorStores;
            return this;
        }

        public MultiVectorStoreDocumentRetriever.Builder similarityThreshold(Double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
            return this;
        }

        public MultiVectorStoreDocumentRetriever.Builder topK(Integer topK) {
            this.topK = topK;
            return this;
        }

        public MultiVectorStoreDocumentRetriever.Builder filterExpression(Filter.Expression filterExpression) {
            this.filterExpression = () -> filterExpression;
            return this;
        }

        public MultiVectorStoreDocumentRetriever.Builder filterExpression(Supplier<Filter.Expression> filterExpression) {
            this.filterExpression = filterExpression;
            return this;
        }

        public MultiVectorStoreDocumentRetriever build() {
            return new MultiVectorStoreDocumentRetriever(this.vectorStores, this.similarityThreshold, this.topK,
                    this.filterExpression);
        }

    }
}

