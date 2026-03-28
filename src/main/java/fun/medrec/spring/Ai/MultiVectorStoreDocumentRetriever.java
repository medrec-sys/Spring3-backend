package fun.medrec.spring.Ai;

import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.List;

public final class MultiVectorStoreDocumentRetriever implements DocumentRetriever {
    private final List<MyVectorStore> vectorStores;
    private final SearchRequest searchRequest;

    public MultiVectorStoreDocumentRetriever(List<MyVectorStore> vectorStores, @Nullable Double similarityThreshold,
                                             Integer topK) {
        this.vectorStores = vectorStores;
        this.searchRequest = SearchRequest.builder()
                .topK(topK)
                .similarityThreshold(similarityThreshold == null ? 0.7 : similarityThreshold)
                .build();
    }



    @NotNull
    @Override
    public List<Document> retrieve(@NotNull Query query) {
        Assert.notNull(query, "query cannot be null");
        SearchRequest newRequest = SearchRequest.from(searchRequest)
                .query(query.text())
                .build();
        return vectorStores.stream().map(vectorStores -> vectorStores.similaritySearch(newRequest)).flatMap(List::stream)
                .toList();
    }

    public static MultiVectorStoreDocumentRetriever.Builder builder() {
        return new MultiVectorStoreDocumentRetriever.Builder();
    }

    public static final class Builder {

        private List<MyVectorStore> vectorStores;

        private Double similarityThreshold;

        private Integer topK;

        private Builder() {
        }

        public MultiVectorStoreDocumentRetriever.Builder vectorStores(List<MyVectorStore> vectorStores) {
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

        public MultiVectorStoreDocumentRetriever build() {
            return new MultiVectorStoreDocumentRetriever(this.vectorStores, this.similarityThreshold, this.topK);
        }

    }
}

