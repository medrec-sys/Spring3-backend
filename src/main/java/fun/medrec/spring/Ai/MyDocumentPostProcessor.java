package fun.medrec.spring.Ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.postretrieval.document.DocumentPostProcessor;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author 彭超
 * @version 1.0
 * @description 自定义后处理检索到的文档
 * @date 2026-04-24 20:45
 */
@Slf4j
public class MyDocumentPostProcessor implements DocumentPostProcessor
{
    /**
     * @description 当前agent的工作流程包含多个库和多个查询，因此文档数会超过topK，需要限制文档数
     */
    @Override
    public @NotNull List<Document> process(@NotNull Query query, @NotNull List<Document> documents) {
        int topK = (Integer) query.context().getOrDefault("topK", 5);

        log.debug("删选docs");
        log.debug("documents: {}", JSON.toJSONString(documents, SerializerFeature.PrettyFormat));
        // 处理文档
        return documents.stream()
                .sorted(Comparator.comparing(this::getSimilarityScore).reversed())  // ✅ 降序
                .limit(topK)  // 只保留 topK 个
                .collect(Collectors.toList());
    }


    /**
     * @description 设置和获取相似度分数
     */
    public Double getSimilarityScore(Document document) {
        return document.getScore();
    }


}
