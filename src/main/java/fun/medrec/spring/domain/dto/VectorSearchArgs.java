package fun.medrec.spring.domain.dto;

import lombok.Data;


@Data
public class VectorSearchArgs {
    private String query;
    private Integer topK;
    private Double similarityThreshold;
}
