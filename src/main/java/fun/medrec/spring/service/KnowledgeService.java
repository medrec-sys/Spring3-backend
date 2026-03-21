package fun.medrec.spring.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Knowledge;
import org.springframework.web.multipart.MultipartFile;

public interface KnowledgeService extends IService<Knowledge> {
    PageVO<Knowledge> getPage(PageDTO<Knowledge> page);

    Integer save(MultipartFile multipartFile, Integer vectorId);

    Integer delete(Integer id);

    Knowledge getById(Integer id);

    void deleteByVectorId(Integer id);
}