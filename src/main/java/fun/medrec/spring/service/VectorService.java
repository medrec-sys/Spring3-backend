package fun.medrec.spring.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.medrec.spring.domain.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Vector;

import java.util.List;

public interface VectorService extends IService<Vector> {
    PageVO<Vector> getPage(PageDTO<Vector> page);

    List<Vector> getByIds(List<Integer> vectorIds);

    MyVectorStore reBuild(Vector vector);

    Integer delete(Integer id);

    Integer create(Vector vector);
}
