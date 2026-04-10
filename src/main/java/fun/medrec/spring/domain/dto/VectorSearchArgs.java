package fun.medrec.spring.domain.dto;

import jakarta.validation.constraints.*;
import lombok.Data;


@Data
public class VectorSearchArgs {
    @NotNull(message = "查询条件不能为空")
    private String query;
    @NotNull(message = "检索返回的文档数不能为空")
    @Min(value = 0, message = "检索返回的文档数不能小于0")
    @Max(value = 100, message = "检索返回的文档数不能大于100")
    private Integer topK;
    @NotNull(message = "相似度不能为空")
    @DecimalMin(value = "0", message = "相似度不能小于0")
    @DecimalMax(value = "1", message = "相似度不能大于1")
    private Double similarityThreshold;
}
