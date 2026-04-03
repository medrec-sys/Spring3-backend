package fun.medrec.spring.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.dto.VectorSearchArgs;
import fun.medrec.spring.domain.entity.Vector;
import org.springframework.ai.document.Document;

import java.util.List;

public interface VectorService extends IService<Vector> {
    PageVO<Vector> getPage(PageDTO<Vector> page);

    List<Vector> getByIds(List<Integer> vectorIds);

    MyVectorStore reBuild(Vector vector);

    Integer delete(Integer id);

    Integer create(Vector vector);

    MyVectorStore createVector(Integer id);

    List<Document> search(MyVectorStore store, VectorSearchArgs args);

    List<Vector> getVectorByAgentId(Integer id);
}
