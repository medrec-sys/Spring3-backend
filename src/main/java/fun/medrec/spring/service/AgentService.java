package fun.medrec.spring.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.medrec.spring.domain.Ai.AiAgent;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Agent;

public interface AgentService extends IService<Agent> {
    PageVO<Agent> getPage(PageDTO<Agent> page);

    void addVector(Integer agentId, Integer vectorId);

    void removeVector(Integer agentId, Integer vectorId);

    AiAgent createAgent(Integer id);

    AiAgent reBuildAgent(Integer id);

    Integer create(Agent agent);
}
