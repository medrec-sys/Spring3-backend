package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.dto.VectorSearchArgs;
import fun.medrec.spring.domain.entity.AgentVector;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.interceptor.UserContext;
import fun.medrec.spring.mapper.AgentVectorMapper;
import fun.medrec.spring.mapper.VectorMapper;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.service.VectorService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VectorServiceImpl extends ServiceImpl<VectorMapper, Vector> implements VectorService {
    private final VectorMapper vectorMapper;

    private final KnowledgeService knowledgeService;
    private final AgentVectorMapper agentVectorMapper;

    public VectorServiceImpl(VectorMapper vectorMapper, KnowledgeService knowledgeService, AgentVectorMapper agentVectorMapper) {
        this.vectorMapper = vectorMapper;
        this.knowledgeService = knowledgeService;
        this.agentVectorMapper = agentVectorMapper;
    }

    @Override
    public PageVO<Vector> getPage(PageDTO<Vector> page) {
        Page<Vector> vectorPage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<Vector> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Vector::getCreateBy, UserContext.getId())
                .orderByDesc(Vector::getCreateTime);

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

    @Override
    public MyVectorStore createVector(Integer id) {
        LambdaQueryWrapper<Vector> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Vector::getId, id);
        Vector vector = vectorMapper.selectOne(queryWrapper);
        if (vector == null) {
            throw new RuntimeException("向量库不存在,id:" + id);
        }
        return new MyVectorStore(vector);
    }

    @Override
    public List<Document> search(MyVectorStore store, VectorSearchArgs args) {
        SearchRequest request = SearchRequest.builder()
                .query(args.getQuery())
                .topK(args.getTopK())
                .similarityThreshold(args.getSimilarityThreshold())
                .build();

        List<Document> documents = store.similaritySearch(request);
        if (documents.isEmpty()) {
            return List.of();
        }
        Document document = documents.getFirst();
        Integer bookId = Integer.parseInt((String)document.getMetadata().get("bookId"));
        Knowledge byId = knowledgeService.getById(bookId);
        documents.forEach(((doc) -> {
            doc.getMetadata().put("url", byId.getPath());
            doc.getMetadata().put("name", byId.getName());
        }));
        return documents;
    }

    @Override
    public List<Vector> getVectorByAgentId(Integer id) {
        LambdaQueryWrapper<AgentVector> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentVector::getAgentId, id);
        List<AgentVector> agentVectors = agentVectorMapper.selectList(wrapper);
        List<Integer> vectorIds = agentVectors.stream().map(AgentVector::getVectorId).toList();
        if (vectorIds.isEmpty()) {
            return List.of();
        }
        return getByIds(vectorIds);
    }
}
