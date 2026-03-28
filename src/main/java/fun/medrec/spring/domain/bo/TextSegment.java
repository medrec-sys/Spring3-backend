package fun.medrec.spring.domain.bo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class TextSegment {
    private int id;
    private String content;
    private Map<String, String> metadata;
    private String summary;

    private List<TextSegment> children = new ArrayList<>();

    public Integer getNodeNum() {
        return children.stream().mapToInt(TextSegment::getNodeNum).sum() + 1;
    }
}
