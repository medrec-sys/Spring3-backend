package fun.medrec.spring.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import fun.medrec.spring.Ai.AiAgent;
import fun.medrec.spring.Ai.MyVectorStore;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Agent;
import fun.medrec.spring.domain.entity.AgentVector;
import fun.medrec.spring.domain.entity.Knowledge;
import fun.medrec.spring.domain.entity.Vector;
import fun.medrec.spring.exception.BusinessException;
import fun.medrec.spring.interceptor.UserContext;
import fun.medrec.spring.mapper.AgentMapper;
import fun.medrec.spring.mapper.AgentVectorMapper;
import fun.medrec.spring.mapper.KnowledgeMapper;
import fun.medrec.spring.service.AgentService;
import fun.medrec.spring.service.KnowledgeService;
import fun.medrec.spring.service.VectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AgentServiceImpl extends ServiceImpl<AgentMapper, Agent> implements AgentService {
    private final AgentMapper agentMapper;

    final
    AgentVectorMapper agentVectorMapper;

    final
    VectorService vectorService;

    final KnowledgeService knowledgeService;

    public AgentServiceImpl(AgentMapper agentMapper, AgentVectorMapper agentVectorMapper, VectorService vectorService, KnowledgeService knowledgeService) {
        this.agentMapper = agentMapper;
        this.agentVectorMapper = agentVectorMapper;
        this.vectorService = vectorService;
        this.knowledgeService = knowledgeService;
    }

    @Override
    public PageVO<Agent> getPage(PageDTO<Agent> page) {
        Page<Agent> agentPage = new Page<>(page.getPageNum(), page.getPageSize());

        LambdaQueryWrapper<Agent> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Agent::getCreateBy, UserContext.getId())
                .orderByDesc(Agent::getCreateTime);

        Page<Agent> result = agentMapper.selectPage(agentPage, queryWrapper);
        return new PageVO<>(result.getTotal(), result.getRecords());
    }

    @Override
    public void addVector(Integer agentId, Integer vectorId) {
        LambdaQueryWrapper<AgentVector> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentVector::getAgentId, agentId).eq(AgentVector::getVectorId, vectorId);
        List<AgentVector> agentVectors = agentVectorMapper.selectList(queryWrapper);
        if (!agentVectors.isEmpty()) {
            throw new BusinessException("请勿重复添加");
        }
        agentVectorMapper.insert(new AgentVector(null, agentId, vectorId));
    }

    @Override
    public AiAgent createAgent(Integer id) {
        Agent agent = getById(id);
        if (agent == null) {
            throw new BusinessException("Ai不存在,id:" + id);
        }
        if (!agent.getCreateBy().equals(UserContext.getId())) {
            throw new BusinessException("无权限操作");
        }


        LambdaQueryWrapper<AgentVector> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentVector::getAgentId, id);
        List<Integer> vectorIds = agentVectorMapper.selectList(queryWrapper).stream().map(AgentVector::getVectorId).toList();

        List<Vector> vectors = vectorService.getByIds(vectorIds);
        List<MyVectorStore> stores = new ArrayList<>();
        for (Vector vector : vectors) {
            MyVectorStore myVectorStore = new MyVectorStore(vector);
            stores.add(myVectorStore);
        }

        return new AiAgent(agent, stores);
    }

    @Override
    public AiAgent reBuildAgent(Integer id) {
        AiAgent.deleteAgent(id);
        return createAgent(id);
    }

    @Override
    public Integer create(Agent agent) {
        agent.setCreateBy(UserContext.getId());
        agent.setId(null);
        agentMapper.insert(agent);
        return agent.getId();
    }

    @Override
    public List<Document> getVectors(AiAgent agent) {
        List<Document> documents = agent.getDocuments();

        if (documents.isEmpty()) {
            return List.of();
        }
        List<Integer> bookId = documents.stream()
                .map(document -> Integer.parseInt((String) document.getMetadata().get("bookId")))
                .distinct()
                .toList();

        List<Knowledge> knowledges = knowledgeService.getByIds(bookId);
        for (Document document: documents) {
            for (Knowledge knowledge: knowledges) {
                if (knowledge.getId().equals(Integer.parseInt((String) document.getMetadata().get("bookId")))) {
                    document.getMetadata().put("url", knowledge.getPath());
                    document.getMetadata().put("name", knowledge.getName());
                }
            }
        }

        return documents;
    }

    @Override
    public void removeVector(Integer agentId, Integer vectorId) {
        LambdaQueryWrapper<AgentVector> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(AgentVector::getAgentId, agentId).eq(AgentVector::getVectorId, vectorId);
        agentVectorMapper.delete(queryWrapper);
    }
}
