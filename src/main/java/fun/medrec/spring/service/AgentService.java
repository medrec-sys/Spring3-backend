package fun.medrec.spring.service;

import com.baomidou.mybatisplus.extension.service.IService;
import fun.medrec.spring.domain.common.PageDTO;
import fun.medrec.spring.domain.common.PageVO;
import fun.medrec.spring.domain.entity.Agent;

public interface AgentService extends IService<Agent> {
    PageVO<Agent> getPage(PageDTO<Agent> page);
}
