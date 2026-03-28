package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.interceptor.UserContext;
import fun.medrec.spring.mapper.VectorMapper;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.service.VectorService;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VectorServiceImpl extends ServiceImpl<VectorMapper, Vector> implements VectorService {
    private final VectorMapper vectorMapper;

    private final KnowledgeService knowledgeService;

    public VectorServiceImpl(VectorMapper vectorMapper, KnowledgeService knowledgeService) {
        this.vectorMapper = vectorMapper;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public PageVO<Vector> getPage(PageDTO<Vector> page) {
        Page<Vector> vectorPage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<Vector> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Vector::getCreateBy, UserContext.getId());

        Page<Vector> result = vectorMapper.selectPage(vectorPage, queryWrapper);
        return new PageVO<>(result.getTotal(), result.getRecords());
    }

    public List<Vector> getByIds(List<Integer> vectorIds) {
        if (vectorIds.isEmpty()) {
            return List.of();
        }
        return vectorMapper.selectByIds(vectorIds);
    }

    @Override
    public MyVectorStore reBuild(Vector vector) {
        MyVectorStore.deleteStore(vector.getId());
        return new MyVectorStore(vector);
    }

    @Override
    @Transactional
    public Integer delete(Integer id) {
        knowledgeService.deleteByVectorId(id);
        LambdaQueryWrapper<Vector> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Vector::getId, id);
        vectorMapper.deleteById(id);
        MyVectorStore.deleteStore(id);
        return id;
    }

    @Override
    @Transactional
    public Integer create(Vector vector) {
        vector.setId(null);
        vector.setCreateBy(UserContext.getId());
        vector.setIndexName("_");
        vector.setPrefix("_");
        vectorMapper.insert(vector);
        vector.setPrefix(vector.getId() + ":");
        vector.setIndexName(vector.getId() + "_index");
        vectorMapper.updateById(vector);
        return vector.getId();
    }
}
