package fun.medrec.spring.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.medrec.spring.domain.bo.FileData;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Knowledge;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface KnowledgeService extends IService<Knowledge> {
    PageVO<Knowledge> getPage(PageDTO<Knowledge> page);

    Integer saveKnowledgeWithTransaction(String name, String path, Integer vectorId, Integer chunk, Integer userId);

    CompletableFuture<Integer> saveAsync(String taskId, FileData fileData, Integer vectorId, Integer userId);

    Integer delete(Integer id);

    Knowledge getById(Integer id);

    List< Knowledge> getByIds(List<Integer> ids);

    void deleteByVectorId(Integer id);

    List<Knowledge> getByVectorId(Integer id);
}