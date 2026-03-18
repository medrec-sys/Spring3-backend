package fun.medrec.spring.domain.common;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageVO<T> {
    private long total = 0;
    private List<T> rows = null;
}