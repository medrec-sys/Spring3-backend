package fun.medrec.spring.domain.bo;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class TextSegment {
    private int id;
    private int bookId;
    private int page;
    private String content;
    private Map<String, String> metadata = new HashMap<>();



    public void setMap(String key, String value) {
        metadata.put(key, value);
    }

    public String getMap(String key) {
        return metadata.get(key);
    }
}
