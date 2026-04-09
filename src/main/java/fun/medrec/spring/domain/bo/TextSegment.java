package fun.medrec.spring.domain.bo;

import lombok.Data;

import java.util.Map;

@Data
public class TextSegment {
    private int id;
    private String content;
    private Map<String, String> metadata;
    private String summary;
}
