package fun.medrec.spring.Ai;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.utils.TextUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.JedisPooled;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
@Slf4j
public class MyVectorStore {
    private EmbeddingModel embeddingModel;
    private RedisVectorStore redisVectorStore;
    private Vector vector;

    private static int MAX_LENGTH;
    private static double SIMILARITY_THRESHOLD;
    private static JedisPooled jedisPooled;
    private static String apiKey;
    private static String baseUrl;
    private static String embeddingName;
    private static StringRedisTemplate stringRedisTemplate;

    @Getter
    private static Cache<Integer, MyVectorStore> storeCache;
    public static void init(StringRedisTemplate stringRedisTemplate, JedisPooled jedisPooled, int maxLength, double similarityThreshold, String baseUrl, String apiKey, String embeddingName) {
        MyVectorStore.stringRedisTemplate = stringRedisTemplate;
        MyVectorStore.jedisPooled = jedisPooled;
        MyVectorStore.MAX_LENGTH = maxLength;
        MyVectorStore.SIMILARITY_THRESHOLD = similarityThreshold;
        MyVectorStore.apiKey = apiKey;
        MyVectorStore.baseUrl = baseUrl;
        MyVectorStore.embeddingName = embeddingName;

        MyVectorStore.storeCache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();
    }

    public static MyVectorStore getStore(Integer id) {
        return storeCache.getIfPresent(id);
    }

    public static void deleteStore(Integer id) {
        storeCache.invalidate(id);
    }

    // 创建向量模型
    private EmbeddingModel createEmbeddingModel(Vector vector) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .build();

        MetadataMode metadataMode = MetadataMode.EMBED;

        OpenAiEmbeddingOptions options = OpenAiEmbeddingOptions.builder()
                .model(embeddingName)  // 指定模型
                .dimensions(vector.getDim())                  // 设置维度
                .build();
        // 2. 创建 EmbeddingModel
        return new OpenAiEmbeddingModel(openAiApi, metadataMode, options);
    }

    // 创建索引
    private void createIndex() {
        String indexName = vector.getIndexName();
        String prefix = vector.getPrefix();
        Integer dim = vector.getDim();

        try {
            stringRedisTemplate.execute((RedisConnection connection) -> {
                byte[][] args = new byte[][]{
                        indexName.getBytes(),
                        "ON".getBytes(),
                        "JSON".getBytes(),
                        "PREFIX".getBytes(),
                        "1".getBytes(),
                        prefix.getBytes(),
                        "SCHEMA".getBytes(),
                        "$.content".getBytes(),
                        "AS".getBytes(),
                        "content".getBytes(),
                        "TEXT".getBytes(),
                        "WEIGHT".getBytes(),
                        "1".getBytes(),
                        "$.embedding".getBytes(),
                        "AS".getBytes(),
                        "embedding".getBytes(),
                        "VECTOR".getBytes(),
                        "HNSW".getBytes(),
                        "10".getBytes(),
                        "TYPE".getBytes(),
                        "FLOAT32".getBytes(),
                        "DIM".getBytes(),
                        dim.toString().getBytes(),
                        "DISTANCE_METRIC".getBytes(),
                        "COSINE".getBytes(),
                        "M".getBytes(),
                        "16".getBytes(),
                        "EF_CONSTRUCTION".getBytes(),
                        "200".getBytes(),
                        "$.id".getBytes(),
                        "AS".getBytes(),
                        "id".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),
                };   // 发送命令
                return connection.execute("FT.CREATE", args);
            });
        } catch (Exception ignored) {
        }


    }

    // 创建文本转换成向量
    private List<float[]> embed(TextUtil.TextData textData, EmbeddingModel embeddingModel) {
        List<String> pageContents = textData.getTexts();

        // 合并
        float[][] embeddingsArray = new float[pageContents.size()][];

        int len = pageContents.size();
        int batch = 10;
        int last = len % batch;
        int n = len / batch + (last == 0 ? 0 : 1);

        ExecutorService executor = Executors.newFixedThreadPool(15);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < n; i++) {

            int start = i * batch;
            int end = (last != 0 && i == n - 1) ? i * batch + last : (i + 1) * batch;
            List<String> batchList = pageContents.subList(start, end);

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                List<float[]> embed = embeddingModel.embed(batchList);
                for (int j = 0; j < embed.size(); j++) {
                    embeddingsArray[start + j] = embed.get(j);
                }
            }, executor);

            futures.add(future);
            log.info(n + ":" + i);
        }


        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        return Arrays.asList(embeddingsArray);
    }

    // 求相似度数组
    private List<Boolean> getSimilarityArray(List<float[]> embeddings) {
        List<Boolean> cosIfCombine = new ArrayList<>();

        for (int i = 0; i < embeddings.size() - 1; i++) {
            float[] embedding = embeddings.get(i);
            float[] embedding2 = embeddings.get(i + 1);
            double cos = cos(embedding, embedding2);
            cosIfCombine.add(cos >= SIMILARITY_THRESHOLD);
        }

        return cosIfCombine;
    }

    // 句子合并
    private TextUtil.TextData merge(TextUtil.TextData textData, List<Boolean> cosIfCombine) {
        List<String> pageContents = textData.getTexts();
        List<Integer> pageNumbers = textData.getIndexes();

        List<String> sentences = new ArrayList<>();
        StringBuilder sentence = new StringBuilder(pageContents.getFirst());
        Integer pageNum = pageNumbers.getFirst();
        List<Integer> pageNums = new ArrayList<>();
        for (int i = 0; i < cosIfCombine.size() - 1; i++) {
            if (cosIfCombine.get(i) && sentence.length() + pageContents.get(i + 1).length() < MAX_LENGTH) {
                sentence.append(pageContents.get(i + 1));
            } else {
                sentences.add(sentence.toString());
                pageNums.add(pageNum);
                pageNum = pageNumbers.get(i + 1);
                sentence = new StringBuilder(pageContents.get(i + 1));
            }
        }

        sentences.add(sentence.toString());
        pageNums.add(pageNum);

        return new TextUtil.TextData(textData.getFileName(), sentences, pageNums);
    }

    private double cos(float[] a, float[] b) {
        INDArray vec1 = Nd4j.create(a);
        INDArray vec2 = Nd4j.create(b);

        INDArray vec2Column = vec2.reshape(vec2.length(), 1);
        double dot = vec1.mmul(vec2Column).getDouble(0);

        double norm1 = vec1.norm2Number().doubleValue();
        double norm2 = vec2.norm2Number().doubleValue();

        return (norm1 * norm2 == 0) ? 0 : (dot / (norm1 * norm2));
    }

    // 删除索引
    private void deleteIndex() {
        String indexName = vector.getIndexName();

        stringRedisTemplate.execute((RedisConnection connection) -> connection.execute("FT.DROPINDEX", indexName.getBytes()));

    }

    public MyVectorStore(Vector vector) {
        this.vector = vector;
        EmbeddingModel embeddingModel = createEmbeddingModel(vector);
        String indexName = vector.getIndexName();
        String prefix = vector.getPrefix();

        createIndex();

        this.redisVectorStore = RedisVectorStore.builder(jedisPooled, embeddingModel)
                .indexName(indexName)
                .prefix(prefix)
                .initializeSchema(true)
                .build();
        this.embeddingModel = embeddingModel;
        log.info("加载向量库:" + vector.getId());
        MyVectorStore.storeCache.put(vector.getId(),  this);
    }

    // 根据语义合并句子
    public TextUtil.TextData mergeSentence(TextUtil.TextData textData) {
        EmbeddingModel em = this.embeddingModel;

        List<float[]> embeddings = embed(textData, em);

        List<Boolean> cosIfCombine = getSimilarityArray(embeddings);

        return merge(textData, cosIfCombine);
    }

    // 添加文档到vectorStore
    public void addDocuments(List<Document> documents) {
        VectorStore vectorStore = this.redisVectorStore;
        // 分批存储
        int batchSize = 10;
        int total = documents.size();
        int n = total / batchSize;
        int last = total % batchSize;
        for (int i = 0; i < n; i++) {
            List<Document> batch = documents.subList(i * batchSize, (i + 1) * batchSize);
            vectorStore.add(batch);
        }
        if (last > 0) {
            List<Document> batch = documents.subList(n * batchSize, total);
            vectorStore.add(batch);
        }
    }

    // 查找相似度
    public List<Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
        return redisVectorStore.similaritySearch(request);
    }

    // 清空知识库
    public void clear() {
        String prefix = vector.getPrefix();

        // 使用 scan 命令遍历所有匹配的键
        Set<String> keysToDelete = new HashSet<>();

        stringRedisTemplate.execute((RedisCallback<Object>) connection -> {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(prefix + "*")
                    .count(100) // 每次扫描100个
                    .build();

            Cursor<byte[]> cursor = connection.scan(options);
            while (cursor.hasNext()) {
                String key = new String(cursor.next(), StandardCharsets.UTF_8);
                keysToDelete.add(key);

                // 每积累1000个键删除一次，避免内存过大
                if (keysToDelete.size() >= 1000) {
                    stringRedisTemplate.delete(keysToDelete);
                    keysToDelete.clear();
                }
            }

            return null;
        });

        // 删除剩余的键
        if (!keysToDelete.isEmpty()) {
            stringRedisTemplate.delete(keysToDelete);
        }
    }

    // 删除文章
    public void delete(int id, int n) {
        int cycle = n / 10 + 1;
        Filter.Expression expression = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("id"),
                new Filter.Value(id + "")
        );
        for (int i = 0; i < cycle; i++) {
            redisVectorStore.delete(expression);
        }
    }

    // 释放资源
    public void release() {
        deleteIndex();
        clear();
    }
}
