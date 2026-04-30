package fun.medrec.spring.Ai;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;
import fun.medrec.spring.utils.ModelUtil;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
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

    private ModelUtil modelUtils;

    @Override
    public @NotNull List<Query> expand(@NotNull Query query) {
        Assert.notNull(query, "query cannot be null");
        String content =  modelUtils.search(query, n);

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


}
