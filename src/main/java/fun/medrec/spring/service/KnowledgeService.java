package fun.medrec.spring.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Knowledge;

public interface KnowledgeService extends IService<Knowledge> {
    PageVO<Knowledge> getPage(PageDTO<Knowledge> page);
}