package fun.medrec.spring.Ai;

import com.alibaba.fastjson.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import fun.medrec.spring.domain.bo.TextSegment;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.utils.TextUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.redis.RedisVectorStore;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import redis.clients.jedis.JedisPooled;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

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

                        // bookId
                        "$.bookId".getBytes(),
                        "AS".getBytes(),
                        "bookId".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // page
                        "$.page".getBytes(),
                        "AS".getBytes(),
                        "page".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // id
                        "$.id".getBytes(),
                        "AS".getBytes(),
                        "id".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // text
                        "$.text".getBytes(),
                        "AS".getBytes(),
                        "text".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // isRoot
                        "$.isRoot".getBytes(),
                        "AS".getBytes(),
                        "isRoot".getBytes(),
                        "TAG".getBytes(),
                        "SEPARATOR".getBytes(),
                        ",".getBytes(),

                        // childrenIds
                        "$.childrenIds".getBytes(),
                        "AS".getBytes(),
                        "childrenIds".getBytes(),
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
    private List<float[]> embed(List<String> texts, EmbeddingModel embeddingModel) {
        int length = texts.size();

        // 1. 使用线程安全的列表存储结果
        List<float[]> result = new CopyOnWriteArrayList<>(new ArrayList<>(Collections.nCopies(length, null)));

        // 2. 动态计算线程池大小（根据CPU核心数和任务数量）
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), length);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 3. 批次大小
        int batchSize = 10;
        int totalBatches = (length + batchSize - 1) / batchSize;

        log.info("开始向量化：总文本数={}, 线程数={}, 批次大小={}, 总批次数={}",
                length, threadCount, batchSize, totalBatches);

        for (int i = 0; i < totalBatches; i++) {
            final int start = i * batchSize;
            final int end = Math.min(start + batchSize, length);
            final int batchIndex = i;

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    List<String> batchList = texts.subList(start, end);

                    // 执行批量向量化
                    List<float[]> embeddings = embeddingModel.embed(batchList);

                    // 将结果放入正确的位置
                    for (int j = 0; j < embeddings.size(); j++) {
                        result.set(start + j, embeddings.get(j));
                    }

                    log.info("批次 {}/{} 完成，处理文本 {} 到 {}，共 {} 条",
                            batchIndex + 1, totalBatches, start + 1, end, embeddings.size());

                } catch (Exception e) {
                    log.error("批次 {}/{} 处理失败: {}", batchIndex + 1, totalBatches, e.getMessage(), e);
                    // 设置空数组作为占位符，避免NPE
                    for (int j = start; j < end; j++) {
                        result.set(j, new float[0]);
                    }
                }
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("线程池未能在60秒内正常关闭，强制终止");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.error("线程池关闭时被中断", e);
            }
        }

        // 检查是否有失败的向量化
        long failedCount = result.stream().filter(arr -> arr == null || arr.length == 0).count();
        if (failedCount > 0) {
            log.warn("向量化完成，但有 {} 条文本向量化失败", failedCount);
        } else {
            log.info("向量化完成，所有 {} 条文本处理成功", length);
        }

        return result;
    }

    //  计算余弦相似度
    private double cos(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
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
                .metadataFields(
                        RedisVectorStore.MetadataField.tag("childrenIds"),
                        RedisVectorStore.MetadataField.tag("bookId"),
                        RedisVectorStore.MetadataField.tag("page"),
                        RedisVectorStore.MetadataField.tag("id"),
                        RedisVectorStore.MetadataField.tag("text")
                )
                .initializeSchema(true)
                .build();
        this.embeddingModel = embeddingModel;
        log.info("加载向量库:{}", vector.getId());
        MyVectorStore.storeCache.put(vector.getId(), this);
    }

    // 添加文档到vectorStore
    public void addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        VectorStore vectorStore = this.redisVectorStore;
        int total = documents.size();
        int batchSize = 10;

        // 计算批次数量
        int batchCount = (total + batchSize - 1) / batchSize;

        // 根据CPU核心数和批次数量设置线程池大小
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), batchCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // 使用线程安全的列表或直接处理
        List<Document> documentList = new CopyOnWriteArrayList<>(documents);

        for (int i = 0; i < batchCount; i++) {
            final int batchIndex = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    int start = batchIndex * batchSize;
                    int end = Math.min(start + batchSize, total);
                    List<Document> batch = documentList.subList(start, end);

                    // 添加日志记录批次处理
                    synchronized (System.out) {
                        log.info("开始处理第 {}/{} 批次，文档数量：{}，范围：[{}-{}]",
                                batchIndex + 1, batchCount, batch.size(), start, end - 1);
                    }

                    // 存储批次文档
                    vectorStore.add(batch);

                    synchronized (System.out) {
                        log.info("完成处理第 {}/{} 批次，文档数量：{}",
                                batchIndex + 1, batchCount, batch.size());
                    }

                } catch (Exception e) {
                    log.error("处理第 {} 批次失败: {}", batchIndex + 1, e.getMessage(), e);
                    // 可以选择重试或者记录失败批次
                    throw new RuntimeException("批次处理失败", e);
                }
            }, executor);

            futures.add(future);
        }

        // 等待所有任务完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            log.info("所有批次处理完成，总文档数：{}，总批次数：{}", total, batchCount);
        } catch (Exception e) {
            log.error("批量添加文档过程中发生异常", e);
            throw new RuntimeException("批量添加文档失败", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                    log.warn("线程池未能在60秒内正常关闭，强制关闭");
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
                log.error("线程池关闭时被中断", e);
            }
        }
    }

    // 查找相似度
    public List<Document> similaritySearch(org.springframework.ai.vectorstore.SearchRequest request) {
        double similarityThreshold = request.getSimilarityThreshold() / 2;
        double step = similarityThreshold / 5;

        List<Document> out = new ArrayList<>();

        SearchRequest rootRequest = SearchRequest.from(request)
                .similarityThreshold(similarityThreshold)
                .filterExpression("isRoot == '1'")
                .build();

        List<String> id = new ArrayList<>(redisVectorStore.similaritySearch(rootRequest)
                .stream()
                .map((doc) -> {
                    String obj = (String) doc.getMetadata().get("childrenIds");
                    String[] array = JSON.parseArray(obj, String.class).toArray(new String[0]);
                    return (List<String>) new ArrayList<>(Arrays.asList(array));
                }).flatMap(List::stream)
                .toList());

        while (!id.isEmpty()) {
            Filter.Expression expression = new FilterExpressionBuilder()
                    .in("id", id.toArray())
                    .build();

            similarityThreshold += step;
            SearchRequest newRequest = SearchRequest.from(request)
                    .similarityThreshold(similarityThreshold)
                    .filterExpression(expression)
                    .build();
            List<Document> documents = redisVectorStore.similaritySearch(newRequest);
            id.clear();
            for (Document document : documents) {
                String childrenIds = (String) document.getMetadata().get("childrenIds");
                if (childrenIds != null) {
                    id.addAll(List.of(JSON.parseArray(childrenIds, String.class).toArray(new String[0])));
                } else {
                    out.add(document);
                }
            }
        }
        return out.stream().map(document -> {
            Map<String, Object> metadata = document.getMetadata();
            String text = (String) metadata.get("text");
            metadata.put("text", document.getText());
            return new Document(text, metadata);
        }).toList();
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
                new Filter.Key("bookId"),
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

    // 构建层
    private List<TextSegment> buildLayer(List<TextSegment> textSegments, List<float[]> embeddings, double similarityThreshold, int index) {
        List<TextSegment> out = new ArrayList<>();
        // 判断是否已经添加到树中,默认False
        List<Boolean> isAdd = new ArrayList<>(Collections.nCopies(textSegments.size(), false));

        int length = textSegments.size();
        for (int i = 0; i < length; i++) {
            if (isAdd.get(i)) {
                continue;
            }
            TextSegment textSegment = new TextSegment();
            textSegment.setId(index);
            index++;
            textSegment.getChildren().add(textSegments.get(i));
            for (int j = i + 1; j < length; j++) {
                if (isAdd.get(j)) {
                    continue;
                }
                double similarity = cos(embeddings.get(i), embeddings.get(j));
                if (similarity > similarityThreshold) {
                    textSegment.getChildren().add(textSegments.get(j));
                    isAdd.set(j, true);
                }
            }
            out.add(textSegment);
        }
        return out;
    }

    // 构建知识树
    public TextSegment buildTree(List<TextSegment> textSegments) {
        double similarityThreshold = 0.8;
        int i = 0;
        int index = textSegments.size();
        while (textSegments.size() > 1) {
            log.info("构建树:{}", i);
            log.info("长度:{}", textSegments.size());
            List<String> text = textSegments.stream()
                    .map(TextSegment::getSummary)
                    .toList();

            log.info("向量化");
            List<float[]> embeddings = embed(text, embeddingModel);

            log.info("开始构建树");
            textSegments = buildLayer(textSegments, embeddings, similarityThreshold, index);

            log.info("生成总结文本");
            textSegments = TextUtil.batchAggregateSummaries(textSegments);

            similarityThreshold -= 0.2;
            i++;
            index += textSegments.size();
        }
        return textSegments.getFirst();

    }

}
